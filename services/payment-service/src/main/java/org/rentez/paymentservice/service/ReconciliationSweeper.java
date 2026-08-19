package org.rentez.paymentservice.service;

import org.rentez.paymentservice.client.ReservationClient;
import org.rentez.paymentservice.domain.ConfirmState;
import org.rentez.paymentservice.domain.Payment;
import org.rentez.paymentservice.repository.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Finishes payments the saga could not.
 *
 * <p>This is the component that makes the difference between a saga and two
 * hopeful writes. Every other part of the flow can be retried by the caller; the
 * cases here are the ones where nobody is left to retry - the customer's request
 * has returned, their card is charged, and something downstream did not complete.
 * Without this, those payments sit forever and the only way to find them is a
 * customer complaint.
 *
 * <p>Two situations, with opposite remedies:
 *
 * <ul>
 *   <li>{@code PENDING} - charged, booking never confirmed. Retry the confirm.
 *       Safe to repeat because reservation's confirm is idempotent.</li>
 *   <li>{@code AWAITING_COMPENSATION} - charged, unconfirmable, and the refund
 *       also failed. Retry the release; the cancel is idempotent too.</li>
 * </ul>
 *
 * <p>Everything it calls is idempotent, which is what lets it run repeatedly
 * without needing to know how far the previous attempt got.
 */
@Component
public class ReconciliationSweeper {

	private static final Logger log = LoggerFactory.getLogger(ReconciliationSweeper.class);
	private static final int BATCH_SIZE = 50;

	private final PaymentRepository paymentRepository;
	private final PaymentTransactions transactions;
	private final ReservationClient reservationClient;

	public ReconciliationSweeper(PaymentRepository paymentRepository, PaymentTransactions transactions,
			ReservationClient reservationClient) {
		this.paymentRepository = paymentRepository;
		this.transactions = transactions;
		this.reservationClient = reservationClient;
	}

	/** Returns how many payments were driven to a terminal state. */
	public int sweep() {
		List<Payment> unfinished = paymentRepository.findUnfinished(
				List.of(ConfirmState.PENDING, ConfirmState.AWAITING_COMPENSATION),
				PageRequest.of(0, BATCH_SIZE));

		int resolved = 0;
		for (Payment payment : unfinished) {
			if (payment.getConfirmState() == ConfirmState.PENDING) {
				if (retryConfirm(payment)) {
					resolved++;
				}
			}
			else if (retryCompensation(payment)) {
				resolved++;
			}
		}
		if (resolved > 0) {
			log.info("Reconciliation resolved {} of {} unfinished payments", resolved, unfinished.size());
		}
		return resolved;
	}

	private boolean retryConfirm(Payment payment) {
		try {
			reservationClient.confirmBooking(payment.getBookingId());
			transactions.markConfirmed(payment.getId());
			return true;
		}
		catch (ReservationClient.BookingUnconfirmableException ex) {
			// The booking is gone for good, so the money has to follow.
			return retryCompensation(refundFor(payment, ex.getMessage()));
		}
		catch (RuntimeException ex) {
			transactions.markSagaFailure(payment.getId(), ConfirmState.PENDING, ex.getMessage());
			return false;
		}
	}

	private Payment refundFor(Payment payment, String reason) {
		try {
			return transactions.markCompensated(payment.getId(), "system");
		}
		catch (RuntimeException ex) {
			return transactions.markSagaFailure(payment.getId(), ConfirmState.AWAITING_COMPENSATION,
					reason + " / refund failed: " + ex.getMessage());
		}
	}

	private boolean retryCompensation(Payment payment) {
		try {
			reservationClient.cancelBooking(payment.getBookingId());
			transactions.markConfirmState(payment.getId(), ConfirmState.COMPENSATED);
			return true;
		}
		catch (RuntimeException ex) {
			transactions.markSagaFailure(payment.getId(), ConfirmState.AWAITING_COMPENSATION,
					ex.getMessage());
			return false;
		}
	}
}

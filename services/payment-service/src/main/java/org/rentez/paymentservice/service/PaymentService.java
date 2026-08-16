package org.rentez.paymentservice.service;

import org.rentez.paymentservice.client.BookingView;
import org.rentez.paymentservice.client.ReservationClient;
import org.rentez.paymentservice.domain.ConfirmState;
import org.rentez.paymentservice.domain.Payment;
import org.rentez.paymentservice.domain.PaymentStatus;
import org.rentez.paymentservice.error.ApiException;
import org.rentez.paymentservice.repository.PaymentRepository;
import org.rentez.paymentservice.web.dto.PaymentRequest;
import org.rentez.paymentservice.web.dto.PaymentResponse;
import org.rentez.paymentservice.web.dto.PaymentStats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * The payment saga.
 *
 * <p>In the monolith this was one method against one database:
 *
 * <pre>
 *   payment = paymentRepository.save(payment);
 *   booking.setStatus(Booking.BookingStatus.CONFIRMED);
 *   bookingRepository.save(booking);
 * </pre>
 *
 * <p>Two writes to two aggregates, with no transaction around them - so it could
 * already leave a SUCCESS payment against a still-PENDING_PAYMENT booking, and
 * the customer could then pay again. Splitting the services does not create that
 * problem; it makes it visible and forces it to be handled.
 *
 * <p><strong>This class holds no transaction.</strong> It interleaves database
 * writes with HTTP calls, and a transaction spanning a network round-trip holds
 * row locks for as long as the far side takes to answer - or to time out. Each
 * database step is a short transaction owned by {@link PaymentTransactions}.
 */
@Service
public class PaymentService {

	private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

	private final PaymentTransactions transactions;
	private final PaymentRepository paymentRepository;
	private final ReservationClient reservationClient;

	public PaymentService(PaymentTransactions transactions, PaymentRepository paymentRepository,
			ReservationClient reservationClient) {
		this.transactions = transactions;
		this.paymentRepository = paymentRepository;
		this.reservationClient = reservationClient;
	}

	/**
	 * Charge for a booking, then confirm it.
	 *
	 * <p>Ordering, and why each step is where it is:
	 *
	 * <ol>
	 *   <li>Replay check - an identical request returns the original payment.</li>
	 *   <li>Read the booking. A failure here is the safe one: nothing charged.</li>
	 *   <li>Write the attempt <em>before</em> the gateway call.</li>
	 *   <li>Call the gateway.</li>
	 *   <li>Record the outcome, then confirm the booking.</li>
	 * </ol>
	 */
	public PaymentResponse pay(Long customerId, String customerEmail, String idempotencyKey,
			PaymentRequest request) {

		String key = idempotencyKey == null || idempotencyKey.isBlank()
				? UUID.randomUUID().toString()
				: idempotencyKey;

		// A retried request must never charge twice. Note this is only the fast
		// path: the unique index below is what actually decides under concurrency.
		Payment replay = transactions.findByIdempotencyKey(key).orElse(null);
		if (replay != null) {
			return PaymentResponse.from(replay);
		}

		BookingView booking = reservationClient.getBooking(request.bookingId());

		if (!booking.isOwnedBy(customerId)) {
			throw new ApiException(HttpStatus.FORBIDDEN, "This booking does not belong to you");
		}
		if (!booking.isAwaitingPayment()) {
			throw new ApiException(HttpStatus.CONFLICT,
					"This booking is not awaiting payment (status: " + booking.status() + ")");
		}

		Payment payment;
		try {
			// The amount comes from reservation, never from the request body.
			payment = transactions.initiate(booking.id(), customerId, customerEmail, key,
					booking.totalAmount(), request.method());
		}
		catch (PaymentTransactions.DuplicateAttemptException ex) {
			return PaymentResponse.from(transactions.findByIdempotencyKey(key)
					.orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, ex.getMessage())));
		}

		if (!simulateGateway(request.cardNumber())) {
			// Committed BEFORE the throw - see markFailed. The monolith relied on
			// exactly this ordering, and the same message and status come back.
			transactions.markFailed(payment.getId(), "Declined by gateway");
			throw new ApiException(HttpStatus.PAYMENT_REQUIRED, "Payment was declined by the gateway");
		}

		Payment charged;
		try {
			charged = transactions.markSuccess(payment.getId());
		}
		catch (PaymentTransactions.AlreadyPaidException ex) {
			// Lost a race with a concurrent payment for the same booking. Both
			// passed the PENDING_PAYMENT check; the unique index settled it.
			transactions.markSagaFailure(payment.getId(), ConfirmState.NOT_APPLICABLE, ex.getMessage());
			throw new ApiException(HttpStatus.CONFLICT, "This booking has already been paid");
		}

		return PaymentResponse.from(confirmOrCompensate(charged));
	}

	/**
	 * Confirms the paid booking, or gives the money back if it cannot be confirmed.
	 *
	 * <p>Three outcomes, and the difference between them is the whole design:
	 *
	 * <ul>
	 *   <li><strong>Confirmed.</strong> Done.</li>
	 *   <li><strong>Refused</strong> (booking cancelled or completed meanwhile).
	 *       Retrying will never help, so compensate immediately.</li>
	 *   <li><strong>Unreachable.</strong> Unknown whether it landed. Leave the
	 *       payment PENDING and let the sweeper retry - the confirm endpoint is
	 *       idempotent precisely so this is safe.</li>
	 * </ul>
	 *
	 * <p>The customer is never told their payment failed here. It did not: their
	 * card was charged, and the booking is either confirmed or refunded shortly.
	 */
	private Payment confirmOrCompensate(Payment payment) {
		try {
			reservationClient.confirmBooking(payment.getBookingId());
			return transactions.markConfirmed(payment.getId());
		}
		catch (ReservationClient.BookingUnconfirmableException ex) {
			return compensate(payment, "system", ex.getMessage());
		}
		catch (RuntimeException ex) {
			log.warn("Could not confirm booking {} for payment {}; leaving for reconciliation: {}",
					payment.getBookingId(), payment.getId(), ex.getMessage());
			return transactions.markSagaFailure(payment.getId(), ConfirmState.PENDING, ex.getMessage());
		}
	}

	/**
	 * Refund and release the booking.
	 *
	 * <p>If the refund itself cannot be completed the payment is parked in
	 * {@code AWAITING_COMPENSATION} rather than being allowed to disappear. A saga
	 * whose compensation can fail silently is not a saga.
	 */
	private Payment compensate(Payment payment, String actorEmail, String reason) {
		try {
			Payment refunded = transactions.markCompensated(payment.getId(), actorEmail);
			reservationClient.cancelBooking(payment.getBookingId());
			return refunded;
		}
		catch (RuntimeException ex) {
			log.error("Compensation failed for payment {} (booking {}): {}",
					payment.getId(), payment.getBookingId(), ex.getMessage());
			return transactions.markSagaFailure(payment.getId(), ConfirmState.AWAITING_COMPENSATION,
					reason + " / compensation failed: " + ex.getMessage());
		}
	}

	/** Admin-initiated refund of an already-successful payment. */
	public PaymentResponse refund(Long paymentId, String actorEmail) {
		Payment payment = transactions.require(paymentId);
		Payment refunded = transactions.markRefunded(payment.getId(), actorEmail);

		try {
			reservationClient.cancelBooking(refunded.getBookingId());
			return PaymentResponse.from(
					transactions.markConfirmState(refunded.getId(), ConfirmState.COMPENSATED));
		}
		catch (RuntimeException ex) {
			// Money is back with the customer; the booking release is owed. The
			// sweeper finishes it rather than the admin being told the refund failed.
			log.warn("Refunded payment {} but could not release booking {}: {}",
					refunded.getId(), refunded.getBookingId(), ex.getMessage());
			return PaymentResponse.from(transactions.markSagaFailure(refunded.getId(),
					ConfirmState.AWAITING_COMPENSATION, ex.getMessage()));
		}
	}

	/**
	 * The mock gateway, ported unchanged: a card number starting with "0000"
	 * always fails, anything else (including none, for a wallet) succeeds. This is
	 * the single seam to swap for a real provider.
	 */
	private boolean simulateGateway(String cardNumber) {
		return cardNumber == null || !cardNumber.startsWith("0000");
	}

	// -------------------------------------------------------------------- reads

	@Transactional(readOnly = true)
	public List<PaymentResponse> historyForBooking(Long bookingId) {
		return paymentRepository.findByBookingIdOrderByCreatedAtDesc(bookingId).stream()
				.map(PaymentResponse::from)
				.toList();
	}

	@Transactional(readOnly = true)
	public List<PaymentResponse> historyForCustomer(Long customerId) {
		return paymentRepository.findByCustomerIdOrderByCreatedAtDesc(customerId).stream()
				.map(PaymentResponse::from)
				.toList();
	}

	@Transactional(readOnly = true)
	public PaymentStats stats() {
		return new PaymentStats(
				paymentRepository.totalRevenue(),
				paymentRepository.countByStatus(PaymentStatus.SUCCESS),
				paymentRepository.countByStatus(PaymentStatus.FAILED),
				paymentRepository.countByStatus(PaymentStatus.REFUNDED),
				paymentRepository.findUnfinished(
						List.of(ConfirmState.PENDING, ConfirmState.AWAITING_COMPENSATION),
						org.springframework.data.domain.PageRequest.of(0, 1000)).size());
	}
}

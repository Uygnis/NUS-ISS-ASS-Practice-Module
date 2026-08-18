package org.rentez.paymentservice.service;

import org.rentez.paymentservice.domain.ConfirmState;
import org.rentez.paymentservice.domain.Payment;
import org.rentez.paymentservice.domain.PaymentMethod;
import org.rentez.paymentservice.domain.PaymentStatus;
import org.rentez.paymentservice.error.ApiException;
import org.rentez.paymentservice.repository.PaymentRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/**
 * Every database write the saga performs, each in its own short transaction.
 *
 * <p>Split from {@link PaymentService} for a specific reason: the saga
 * interleaves database writes with HTTP calls to reservation, and an HTTP call
 * inside a transaction holds row locks open for the duration of a network
 * round-trip - including the case where the far side has stopped answering. The
 * orchestration therefore owns no transaction, and each step here opens and
 * closes one.
 *
 * <p>A separate bean rather than private methods, because {@code @Transactional}
 * is proxy-based: self-invocation would silently run everything in whatever
 * transaction happened to be open, defeating the split entirely.
 */
@Service
public class PaymentTransactions {

	private final PaymentRepository paymentRepository;
	private final AuditService auditService;
	private final OutboxWriter outbox;

	public PaymentTransactions(PaymentRepository paymentRepository, AuditService auditService,
			OutboxWriter outbox) {
		this.paymentRepository = paymentRepository;
		this.auditService = auditService;
		this.outbox = outbox;
	}

	@Transactional(readOnly = true)
	public Optional<Payment> findByIdempotencyKey(String key) {
		return paymentRepository.findByIdempotencyKey(key);
	}

	@Transactional(readOnly = true)
	public Payment require(Long paymentId) {
		return paymentRepository.findById(paymentId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "No payment with id " + paymentId));
	}

	/**
	 * Writes the attempt BEFORE the gateway is called.
	 *
	 * <p>This ordering is the whole point. The monolith constructed its Payment
	 * from the gateway's answer and saved afterwards, so a crash between the
	 * charge and the insert left money moved and no record of it. Recording the
	 * intent first means the worst case is a stranded INITIATED row - visible,
	 * reconcilable, and far better than a silent hole.
	 */
	@Transactional
	public Payment initiate(Long bookingId, Long customerId, String customerEmail, String idempotencyKey,
			BigDecimal amount, PaymentMethod method) {
		try {
			return paymentRepository.saveAndFlush(
					new Payment(bookingId, customerId, customerEmail, idempotencyKey, amount, method));
		}
		catch (DataIntegrityViolationException ex) {
			// Concurrent submissions with the same Idempotency-Key. The other one
			// owns the attempt; signal a replay rather than starting a second.
			throw new DuplicateAttemptException(idempotencyKey);
		}
	}

	/**
	 * Records a successful charge, its receipt and its audit entry atomically.
	 *
	 * @throws AlreadyPaidException when {@code uk_payment_success_booking} rejects
	 *     a second successful payment for the same booking. Two concurrent
	 *     requests can both read a booking as PENDING_PAYMENT, so the database is
	 *     what actually decides.
	 */
	@Transactional
	public Payment markSuccess(Long paymentId) {
		Payment payment = require(paymentId);
		payment.succeeded("TXN-" + UUID.randomUUID());
		try {
			Payment saved = paymentRepository.saveAndFlush(payment);
			auditService.log(saved.getCustomerEmail(), "PAYMENT_SUCCESS", "Payment", saved.getId(),
					"Booking #" + saved.getBookingId());
			outbox.paymentReceipt(saved);
			return saved;
		}
		catch (DataIntegrityViolationException ex) {
			throw new AlreadyPaidException(payment.getBookingId());
		}
	}

	/**
	 * Records a declined charge and its audit entry.
	 *
	 * <p>Committed before the caller throws, deliberately. The monolith did the
	 * same and it was load-bearing: persisting the FAILED payment and its
	 * PAYMENT_FAILED audit row inside a transaction that then throws would roll
	 * both back and erase every record of declined payments.
	 */
	@Transactional
	public Payment markFailed(Long paymentId, String reason) {
		Payment payment = require(paymentId);
		payment.declined(reason);
		Payment saved = paymentRepository.save(payment);
		auditService.log(saved.getCustomerEmail(), "PAYMENT_FAILED", "Payment", saved.getId(),
				"Booking #" + saved.getBookingId());
		return saved;
	}

	@Transactional
	public Payment markConfirmed(Long paymentId) {
		return markConfirmState(paymentId, ConfirmState.CONFIRMED);
	}

	/** Moves the saga to a terminal state and clears any recorded error. */
	@Transactional
	public Payment markConfirmState(Long paymentId, ConfirmState state) {
		Payment payment = require(paymentId);
		payment.confirmState(state);
		return paymentRepository.save(payment);
	}

	/** Money returned and the booking released - the terminal compensated state. */
	@Transactional
	public Payment markCompensated(Long paymentId, String actorEmail) {
		Payment payment = require(paymentId);
		payment.refunded();
		payment.confirmState(ConfirmState.COMPENSATED);
		Payment saved = paymentRepository.save(payment);

		auditService.log(actorEmail, "REFUND", "Payment", saved.getId(),
				"Booking #" + saved.getBookingId());
		outbox.refundProcessed(saved);
		return saved;
	}

	/**
	 * Parks a payment the saga could not finish, so the sweeper can find it.
	 *
	 * <p>The alternative is losing track of a charged card, which is the one
	 * outcome a payment service must never produce.
	 */
	@Transactional
	public Payment markSagaFailure(Long paymentId, ConfirmState state, String error) {
		Payment payment = require(paymentId);
		payment.recordSagaFailure(state, error);
		return paymentRepository.save(payment);
	}

	/** Admin-initiated refund of an already-successful payment. */
	@Transactional
	public Payment markRefunded(Long paymentId, String actorEmail) {
		Payment payment = require(paymentId);
		if (payment.getStatus() != PaymentStatus.SUCCESS) {
			throw new ApiException(HttpStatus.CONFLICT, "Only successful payments can be refunded");
		}
		payment.refunded();
		Payment saved = paymentRepository.save(payment);

		auditService.log(actorEmail, "REFUND", "Payment", saved.getId(),
				"Booking #" + saved.getBookingId());
		outbox.refundProcessed(saved);
		return saved;
	}

	/** Signals that an identical request is already in flight or complete. */
	public static class DuplicateAttemptException extends RuntimeException {

		private final String idempotencyKey;

		public DuplicateAttemptException(String idempotencyKey) {
			super("Duplicate payment attempt for idempotency key " + idempotencyKey);
			this.idempotencyKey = idempotencyKey;
		}

		public String getIdempotencyKey() {
			return idempotencyKey;
		}
	}

	/** The database refused a second successful payment for one booking. */
	public static class AlreadyPaidException extends RuntimeException {

		private final Long bookingId;

		public AlreadyPaidException(Long bookingId) {
			super("Booking " + bookingId + " has already been paid");
			this.bookingId = bookingId;
		}

		public Long getBookingId() {
			return bookingId;
		}
	}
}

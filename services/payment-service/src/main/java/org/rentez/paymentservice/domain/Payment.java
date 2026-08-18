package org.rentez.paymentservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A payment attempt against a booking.
 *
 * <p>The monolith's version held {@code @ManyToOne Booking booking}, and through
 * it reached two more domains -
 * {@code payment.getBooking().getCustomer()} was how a receipt found its
 * recipient. That is a {@code bookingId} plus a snapshot now, so nothing here
 * dereferences another service's data.
 *
 * <p>The card number is accepted only to drive the mock gateway and is
 * <strong>never stored</strong>, exactly as before. There is deliberately no
 * column for it, no PAN, no CVV and no expiry - which is what keeps this service
 * out of PCI scope entirely.
 */
@Entity
@Table(name = "payment")
public class Payment {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "booking_id", nullable = false)
	private Long bookingId;

	@Column(name = "customer_id", nullable = false)
	private Long customerId;

	@Column(name = "customer_email", nullable = false, length = 255)
	private String customerEmail;

	@Column(name = "idempotency_key", nullable = false, unique = true, length = 64)
	private String idempotencyKey;

	@Column(nullable = false, precision = 12, scale = 2)
	private BigDecimal amount;

	@Column(nullable = false, length = 3)
	private String currency = "SGD";

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private PaymentMethod method;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private PaymentStatus status = PaymentStatus.INITIATED;

	@Enumerated(EnumType.STRING)
	@Column(name = "confirm_state", nullable = false, length = 24)
	private ConfirmState confirmState = ConfirmState.PENDING;

	@Column(name = "transaction_ref", unique = true, length = 64)
	private String transactionRef;

	@Column(name = "failure_reason", length = 255)
	private String failureReason;

	@Column(name = "attempt_count", nullable = false)
	private int attemptCount = 0;

	@Column(name = "last_error", length = 1000)
	private String lastError;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt = Instant.now();

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt = Instant.now();

	/**
	 * Read-only mirror of the generated column that enforces "at most one
	 * successful payment per booking". Mapped so Hibernate's schema validation
	 * accounts for it; never written from Java.
	 */
	@Column(name = "succeeded_booking_id", insertable = false, updatable = false)
	private Long succeededBookingId;

	protected Payment() {
	}

	public Payment(Long bookingId, Long customerId, String customerEmail, String idempotencyKey,
			BigDecimal amount, PaymentMethod method) {
		this.bookingId = bookingId;
		this.customerId = customerId;
		this.customerEmail = customerEmail;
		this.idempotencyKey = idempotencyKey;
		this.amount = amount;
		this.method = method;
	}

	@PreUpdate
	void touch() {
		this.updatedAt = Instant.now();
	}

	public void succeeded(String transactionRef) {
		this.status = PaymentStatus.SUCCESS;
		this.transactionRef = transactionRef;
		this.failureReason = null;
	}

	public void declined(String reason) {
		this.status = PaymentStatus.FAILED;
		this.failureReason = reason;
		// A declined payment never confirmed anything, so there is nothing for the
		// sweeper to finish.
		this.confirmState = ConfirmState.NOT_APPLICABLE;
	}

	public void refunded() {
		this.status = PaymentStatus.REFUNDED;
	}

	public void confirmState(ConfirmState state) {
		this.confirmState = state;
		this.lastError = null;
	}

	public void recordSagaFailure(ConfirmState state, String error) {
		this.confirmState = state;
		this.attemptCount++;
		this.lastError = error == null ? null : error.substring(0, Math.min(error.length(), 1000));
	}

	public Long getId() { return id; }
	public Long getBookingId() { return bookingId; }
	public Long getCustomerId() { return customerId; }
	public String getCustomerEmail() { return customerEmail; }
	public String getIdempotencyKey() { return idempotencyKey; }
	public BigDecimal getAmount() { return amount; }
	public String getCurrency() { return currency; }
	public PaymentMethod getMethod() { return method; }
	public PaymentStatus getStatus() { return status; }
	public ConfirmState getConfirmState() { return confirmState; }
	public String getTransactionRef() { return transactionRef; }
	public String getFailureReason() { return failureReason; }
	public int getAttemptCount() { return attemptCount; }
	public String getLastError() { return lastError; }
	public Instant getCreatedAt() { return createdAt; }
	public Instant getUpdatedAt() { return updatedAt; }
}

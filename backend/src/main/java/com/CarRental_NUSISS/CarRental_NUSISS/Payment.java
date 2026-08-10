package com.CarRental_NUSISS.CarRental_NUSISS;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

/** A payment attempt against a booking, processed by the mock {@code PaymentService} gateway. */
@Entity
@Table(name = "payment")
public class Payment {

	public enum Method { CARD, PAYPAL, WALLET }
	public enum Status { SUCCESS, FAILED, REFUNDED }

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(optional = false)
	@JoinColumn(name = "booking_id")
	private Booking booking;

	@Column(nullable = false)
	private BigDecimal amount;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Method method;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Status status;

	@Column(nullable = false, unique = true)
	private String transactionRef;

	@Column(nullable = false)
	private Instant createdAt = Instant.now();

	protected Payment() {
	}

	public Payment(Booking booking, BigDecimal amount, Method method, Status status, String transactionRef) {
		this.booking = booking;
		this.amount = amount;
		this.method = method;
		this.status = status;
		this.transactionRef = transactionRef;
	}

	public Long getId() { return id; }
	public Booking getBooking() { return booking; }
	public BigDecimal getAmount() { return amount; }
	public Method getMethod() { return method; }
	public Status getStatus() { return status; }
	public void setStatus(Status status) { this.status = status; }
	public String getTransactionRef() { return transactionRef; }
	public Instant getCreatedAt() { return createdAt; }
}

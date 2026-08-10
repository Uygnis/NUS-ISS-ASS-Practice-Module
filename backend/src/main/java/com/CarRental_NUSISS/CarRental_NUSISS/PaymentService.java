package com.CarRental_NUSISS.CarRental_NUSISS;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.UUID;

/**
 * Mock payment gateway. There is no real card network call here - {@link #simulateGateway}
 * is the single seam to swap for a real provider (Stripe, Braintree, ...). The rest of the
 * flow (booking confirmation, receipts, audit trail) stays the same either way.
 */
@Service
public class PaymentService {

	private final PaymentRepository paymentRepository;
	private final BookingRepository bookingRepository;
	private final NotificationService notificationService;
	private final AuditService auditService;

	public PaymentService(PaymentRepository paymentRepository, BookingRepository bookingRepository,
			NotificationService notificationService, AuditService auditService) {
		this.paymentRepository = paymentRepository;
		this.bookingRepository = bookingRepository;
		this.notificationService = notificationService;
		this.auditService = auditService;
	}

	public Payment pay(User customer, PaymentRequest request) {
		Booking booking = bookingRepository.findById(request.bookingId())
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "No booking with id " + request.bookingId()));

		if (!booking.getCustomer().getId().equals(customer.getId())) {
			throw new ApiException(HttpStatus.FORBIDDEN, "This booking does not belong to you");
		}
		if (booking.getStatus() != Booking.BookingStatus.PENDING_PAYMENT) {
			throw new ApiException(HttpStatus.CONFLICT, "This booking is not awaiting payment (status: " + booking.getStatus() + ")");
		}

		boolean approved = simulateGateway(request);
		Payment payment = new Payment(booking, booking.getTotalAmount(), request.method(),
				approved ? Payment.Status.SUCCESS : Payment.Status.FAILED, "TXN-" + UUID.randomUUID());
		payment = paymentRepository.save(payment);

		auditService.log(customer.getEmail(), approved ? "PAYMENT_SUCCESS" : "PAYMENT_FAILED",
				"Payment", payment.getId(), "Booking #" + booking.getId());

		if (!approved) {
			throw new ApiException(HttpStatus.PAYMENT_REQUIRED, "Payment was declined by the gateway");
		}

		booking.setStatus(Booking.BookingStatus.CONFIRMED);
		bookingRepository.save(booking);
		notificationService.paymentReceipt(payment);
		notificationService.bookingConfirmation(booking);
		return payment;
	}

	/** Admin-initiated refund for an already-successful payment. */
	public Payment refund(Long paymentId, String actorEmail) {
		Payment payment = paymentRepository.findById(paymentId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "No payment with id " + paymentId));
		if (payment.getStatus() != Payment.Status.SUCCESS) {
			throw new ApiException(HttpStatus.CONFLICT, "Only successful payments can be refunded");
		}

		payment.setStatus(Payment.Status.REFUNDED);
		paymentRepository.save(payment);

		Booking booking = payment.getBooking();
		booking.setStatus(Booking.BookingStatus.CANCELLED);
		bookingRepository.save(booking);

		notificationService.refundProcessed(payment);
		auditService.log(actorEmail, "REFUND", "Payment", paymentId, "Booking #" + booking.getId());
		return payment;
	}

	public List<Payment> historyFor(Long bookingId) {
		return paymentRepository.findByBookingId(bookingId);
	}

	/**
	 * Deterministic mock so the flow is testable: a card number starting with "0000"
	 * always fails, everything else (including no card number, e.g. wallet) succeeds.
	 */
	private boolean simulateGateway(PaymentRequest request) {
		return request.cardNumber() == null || !request.cardNumber().startsWith("0000");
	}
}

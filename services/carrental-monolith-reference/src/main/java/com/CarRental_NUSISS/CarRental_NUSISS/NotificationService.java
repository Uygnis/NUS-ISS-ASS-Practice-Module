package com.CarRental_NUSISS.CarRental_NUSISS;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Sends user-facing notifications. In this prototype "sending" means persisting a
 * Notification row and logging it - swap the body of {@link #send} for a real
 * email/SMS/push provider later without touching any caller.
 */
@Service
public class NotificationService {

	private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

	private final NotificationRepository notificationRepository;

	public NotificationService(NotificationRepository notificationRepository) {
		this.notificationRepository = notificationRepository;
	}

	public void send(User recipient, String type, String message) {
		notificationRepository.save(new Notification(recipient, type, message));
		log.info("[notification -> {}] ({}) {}", recipient.getEmail(), type, message);
	}

	public void bookingConfirmation(Booking booking) {
		send(booking.getCustomer(), "BOOKING_CONFIRMED",
				"Your booking #%d for %s %s from %s to %s is confirmed."
						.formatted(booking.getId(), booking.getCar().getMake(), booking.getCar().getModel(),
								booking.getStartDate(), booking.getEndDate()));
	}

	public void bookingCancelled(Booking booking) {
		send(booking.getCustomer(), "BOOKING_CANCELLED",
				"Your booking #%d has been cancelled.".formatted(booking.getId()));
	}

	public void paymentReceipt(Payment payment) {
		send(payment.getBooking().getCustomer(), "PAYMENT_RECEIPT",
				"Payment of %s received for booking #%d (ref %s)."
						.formatted(payment.getAmount(), payment.getBooking().getId(), payment.getTransactionRef()));
	}

	public void refundProcessed(Payment payment) {
		send(payment.getBooking().getCustomer(), "REFUND_PROCESSED",
				"Refund of %s issued for booking #%d.".formatted(payment.getAmount(), payment.getBooking().getId()));
	}
}

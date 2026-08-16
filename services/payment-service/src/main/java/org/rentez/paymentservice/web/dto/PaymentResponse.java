package org.rentez.paymentservice.web.dto;

import org.rentez.paymentservice.domain.ConfirmState;
import org.rentez.paymentservice.domain.Payment;
import org.rentez.paymentservice.domain.PaymentMethod;
import org.rentez.paymentservice.domain.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;

/** The public shape of a payment. No card data exists to leak. */
public record PaymentResponse(
		Long id,
		Long bookingId,
		BigDecimal amount,
		String currency,
		PaymentMethod method,
		PaymentStatus status,
		ConfirmState confirmState,
		String transactionRef,
		String failureReason,
		Instant createdAt) {

	public static PaymentResponse from(Payment payment) {
		return new PaymentResponse(
				payment.getId(),
				payment.getBookingId(),
				payment.getAmount(),
				payment.getCurrency(),
				payment.getMethod(),
				payment.getStatus(),
				payment.getConfirmState(),
				payment.getTransactionRef(),
				payment.getFailureReason(),
				payment.getCreatedAt());
	}
}

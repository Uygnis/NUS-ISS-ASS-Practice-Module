package org.rentez.paymentservice.web.dto;

import jakarta.validation.constraints.NotNull;
import org.rentez.paymentservice.domain.PaymentMethod;

/**
 * Unchanged in shape from the monolith, and deliberately so: no amount field.
 *
 * <p>The charge is always the booking's own total, read from reservation. Letting
 * a client name the price is the oldest mistake in payment APIs, and the monolith
 * already avoided it.
 *
 * <p>Card details drive the mock gateway and are never persisted. A real
 * integration would swap this for a payment-intent or token flow and never see a
 * raw card number at all.
 */
public record PaymentRequest(
		@NotNull Long bookingId,
		@NotNull PaymentMethod method,
		String cardNumber) {
}

package com.CarRental_NUSISS.CarRental_NUSISS;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Card details are accepted only to simulate a gateway call in this prototype -
 * they are never persisted. A real integration would swap this for a payment-
 * intent / token flow (e.g. Stripe) and never see the raw card number at all.
 */
public record PaymentRequest(
		@NotNull Long bookingId,
		@NotNull Payment.Method method,
		String cardNumber) {
}

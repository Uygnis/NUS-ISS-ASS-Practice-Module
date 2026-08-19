package org.rentez.paymentservice.client;

import java.math.BigDecimal;

/**
 * Payment's view of a booking, from
 * {@code GET /api/reservations/internal/bookings/{id}}.
 *
 * <p>Only the fields payment actually needs: who owns it, what it costs, and what
 * state it is in. {@code status} is a String rather than a mirrored enum so
 * reservation can add a booking state without breaking deserialisation here.
 *
 * <p>{@code totalAmount} is the authoritative figure. The monolith already
 * refused to trust a client-supplied amount - it charged
 * {@code booking.getTotalAmount()} - and that property has to survive the split,
 * because "how much do I owe" is now an answer from another service rather than
 * a field on an object in hand.
 */
public record BookingView(
		Long id,
		Long customerId,
		Long carId,
		BigDecimal totalAmount,
		String status) {

	public boolean isAwaitingPayment() {
		return "PENDING_PAYMENT".equals(status);
	}

	public boolean isOwnedBy(Long userId) {
		return customerId != null && customerId.equals(userId);
	}
}

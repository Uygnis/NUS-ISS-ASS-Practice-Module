package org.rentez.accountservice.client;

import java.util.Map;

/** Reservation's slice, from {@code GET /api/reservations/internal/stats}. */
public record ReservationStats(
		long totalBookings,
		long confirmedBookings,
		long cancelledBookings,
		Map<String, Long> bookingsByCarType) {
}

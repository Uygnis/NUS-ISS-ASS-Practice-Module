package org.rentez.reservationservice.web.dto;

import java.util.Map;

/**
 * Reservation's slice of the admin report, answered locally.
 *
 * <p>{@code bookingsByCarType} is the interesting one. The monolith's
 * ReportService produced it with
 * {@code bookings.stream().collect(groupingBy(b -> b.getCar().getType().name()))}
 * - an in-memory group over every booking, each dereferencing a Car. Because the
 * car type is snapshotted onto the booking row, it is now a GROUP BY in the
 * database with no cross-service read at all.
 */
public record ReservationStats(
		long totalBookings,
		long confirmedBookings,
		long cancelledBookings,
		Map<String, Long> bookingsByCarType) {
}

package org.rentez.accountservice.web.dto;

import org.rentez.accountservice.client.CatalogStats;
import org.rentez.accountservice.client.PaymentStats;
import org.rentez.accountservice.client.ReservationStats;

import java.math.BigDecimal;
import java.util.Map;

/**
 * The admin dashboard summary, composed from three services.
 *
 * <p>Field-for-field the same numbers the monolith's {@code ReportSummary}
 * carried, with one addition: {@code partial}.
 *
 * <p>That flag exists because the monolith could not fail halfway. It read three
 * repositories in one transaction, so the summary was always complete or the
 * request errored. Composed across services, a section can simply be missing -
 * and quietly rendering a zero would be worse than useless: "revenue: 0" reads as
 * a business fact, not as "payment-service did not answer". Missing sections are
 * {@code null} and {@code partial} says so.
 */
public record ReportSummary(
		Long totalCars,
		Long availableCars,
		Long carsInMaintenance,
		Long totalBookings,
		Long confirmedBookings,
		Long cancelledBookings,
		BigDecimal totalRevenue,
		Map<String, Long> bookingsByCarType,
		boolean partial,
		java.util.List<String> unavailableSections) {

	public static ReportSummary of(CatalogStats catalog, ReservationStats reservation, PaymentStats payment) {
		java.util.List<String> missing = new java.util.ArrayList<>();
		if (catalog == null) {
			missing.add("catalog");
		}
		if (reservation == null) {
			missing.add("reservation");
		}
		if (payment == null) {
			missing.add("payment");
		}

		return new ReportSummary(
				catalog == null ? null : catalog.totalCars(),
				catalog == null ? null : catalog.availableCars(),
				catalog == null ? null : catalog.inMaintenanceCars(),
				reservation == null ? null : reservation.totalBookings(),
				reservation == null ? null : reservation.confirmedBookings(),
				reservation == null ? null : reservation.cancelledBookings(),
				payment == null ? null : payment.totalRevenue(),
				reservation == null ? null : reservation.bookingsByCarType(),
				!missing.isEmpty(),
				missing);
	}
}

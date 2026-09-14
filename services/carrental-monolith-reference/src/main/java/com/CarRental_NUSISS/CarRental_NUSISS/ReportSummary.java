package com.CarRental_NUSISS.CarRental_NUSISS;

import java.math.BigDecimal;
import java.util.Map;

public record ReportSummary(
		long totalCars,
		long availableCars,
		long carsInMaintenance,
		long totalBookings,
		long confirmedBookings,
		long cancelledBookings,
		BigDecimal totalRevenue,
		Map<String, Long> bookingsByCarType) {
}

package com.CarRental_NUSISS.CarRental_NUSISS;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/** New dates (and optionally pickup location) for an existing booking. */
public record BookingUpdateRequest(
		@NotNull @FutureOrPresent LocalDate startDate,
		@NotNull @FutureOrPresent LocalDate endDate,
		String pickupLocation) {
}

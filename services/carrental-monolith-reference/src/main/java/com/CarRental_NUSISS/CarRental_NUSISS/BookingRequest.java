package com.CarRental_NUSISS.CarRental_NUSISS;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.FutureOrPresent;
import java.time.LocalDate;

public record BookingRequest(
		@NotNull Long carId,
		@NotNull @FutureOrPresent LocalDate startDate,
		@NotNull @FutureOrPresent LocalDate endDate,
		String pickupLocation) {
}

package com.CarRental_NUSISS.CarRental_NUSISS;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record CarRequest(
		@NotBlank String make,
		@NotBlank String model,
		@Positive int year,
		@NotNull @Positive BigDecimal dailyRate,
		@NotBlank String location,
		@NotNull Car.CarType type) {
}

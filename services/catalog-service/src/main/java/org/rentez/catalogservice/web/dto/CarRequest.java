package org.rentez.catalogservice.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.rentez.catalogservice.domain.CarType;

import java.math.BigDecimal;

public record CarRequest(
		@NotBlank @Size(max = 80) String make,
		@NotBlank @Size(max = 80) String model,
		@Positive int year,
		@NotNull @Positive BigDecimal dailyRate,
		@NotBlank @Size(max = 120) String location,
		@NotNull CarType type) {
}

package org.rentez.reservationservice.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record BookingUpdateRequest(
		@NotNull LocalDate startDate,
		@NotNull LocalDate endDate,
		@Size(max = 120) String pickupLocation) {
}

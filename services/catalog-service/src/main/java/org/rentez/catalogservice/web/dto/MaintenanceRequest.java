package org.rentez.catalogservice.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record MaintenanceRequest(
		@NotNull Long carId,
		@NotBlank @Size(max = 500) String description,
		@NotNull LocalDate scheduledDate) {
}

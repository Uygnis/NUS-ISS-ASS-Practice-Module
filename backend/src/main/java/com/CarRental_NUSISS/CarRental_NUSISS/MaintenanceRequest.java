package com.CarRental_NUSISS.CarRental_NUSISS;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record MaintenanceRequest(@NotNull Long carId, @NotBlank String description, @NotNull LocalDate scheduledDate) {
}

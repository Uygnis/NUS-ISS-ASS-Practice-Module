package com.CarRental_NUSISS.CarRental_NUSISS;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(@NotBlank String email, @NotBlank String password) {
}

package com.CarRental_NUSISS.CarRental_NUSISS;

import jakarta.validation.constraints.NotBlank;

public record UpdateProfileRequest(@NotBlank String fullName, String phone) {
}

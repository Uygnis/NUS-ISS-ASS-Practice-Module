package com.CarRental_NUSISS.CarRental_NUSISS;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
		@NotBlank String fullName,
		@NotBlank @Email String email,
		@NotBlank @Size(min = 8, message = "Password must be at least 8 characters") String password,
		String phone) {
}

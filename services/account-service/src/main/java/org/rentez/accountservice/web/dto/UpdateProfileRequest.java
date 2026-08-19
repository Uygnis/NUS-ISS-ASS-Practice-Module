package org.rentez.accountservice.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
		@NotBlank @Size(max = 150) String fullName,
		@Size(max = 20) String phone) {
}

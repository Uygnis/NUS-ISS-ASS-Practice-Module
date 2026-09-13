package com.CarRental_NUSISS.CarRental_NUSISS;

public record AuthResponse(String token, String tokenType, Long userId, String fullName, User.Role role) {
	public AuthResponse(String token, Long userId, String fullName, User.Role role) {
		this(token, "Bearer", userId, fullName, role);
	}
}

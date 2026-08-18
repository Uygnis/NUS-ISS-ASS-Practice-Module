package org.rentez.accountservice.web.dto;

import org.rentez.accountservice.domain.Role;
import org.rentez.accountservice.domain.User;

/** What a successful register or login returns: the bearer token plus enough identity to render a header. */
public record AuthResponse(String token, String tokenType, Long userId, String fullName, Role role) {

	public static AuthResponse of(String token, User user) {
		return new AuthResponse(token, "Bearer", user.getId(), user.getFullName(), user.getRole());
	}
}

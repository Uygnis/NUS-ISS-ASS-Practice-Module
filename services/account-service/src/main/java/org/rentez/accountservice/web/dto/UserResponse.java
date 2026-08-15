package org.rentez.accountservice.web.dto;

import org.rentez.accountservice.domain.Role;
import org.rentez.accountservice.domain.User;

import java.time.Instant;

/**
 * The public shape of a user. <strong>This type is the fix for a live data
 * leak.</strong>
 *
 * <p>The monolith had no DTO layer at all: controllers returned the {@code User}
 * entity directly, {@code User.getPasswordHash()} was public, and there was not
 * a single Jackson annotation anywhere in the codebase. The result was that
 * every BCrypt hash was serialised into the response body of
 * {@code GET /api/users/me} and {@code GET /api/admin/users} - and into every
 * nested customer on a booking or payment.
 *
 * <p>The safety here is structural rather than declarative: there is no
 * {@code passwordHash} component to forget to annotate, and {@link #from} is the
 * only way to build one.
 */
public record UserResponse(
		Long id,
		String fullName,
		String email,
		String phone,
		Role role,
		boolean enabled,
		Instant createdAt) {

	public static UserResponse from(User user) {
		return new UserResponse(
				user.getId(),
				user.getFullName(),
				user.getEmail(),
				user.getPhone(),
				user.getRole(),
				user.isEnabled(),
				user.getCreatedAt());
	}
}

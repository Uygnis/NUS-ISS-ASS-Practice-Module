package org.rentez.paymentservice.security;

import org.springframework.security.oauth2.jwt.Jwt;

/**
 * The caller, resolved purely from the verified JWT - no database read and, more
 * importantly, no call to account-service.
 *
 * <p>{@code role} is a plain String here rather than an enum. Authorization runs
 * off Spring authorities derived from the same claim, so this field is only ever
 * informational; mirroring account-service's {@code Role} enum into all five
 * services would create a type that has to be changed in five places at once.
 */
public record CurrentUser(Long id, String email, String role) {

	public static CurrentUser from(Jwt jwt) {
		// Small integral claims deserialise as Integer, larger ones as Long.
		Number userId = jwt.getClaim("userId");
		return new CurrentUser(
				userId == null ? null : userId.longValue(),
				jwt.getSubject(),
				jwt.getClaimAsString("role"));
	}
}

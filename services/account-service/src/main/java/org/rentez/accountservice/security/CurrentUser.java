package org.rentez.accountservice.security;

import org.rentez.accountservice.domain.Role;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * The caller, resolved purely from the verified JWT.
 *
 * <p>This replaces the monolith's {@code CurrentUserProvider}, which ran a
 * {@code userRepository.findByEmail(...)} on <em>every authenticated request</em>
 * and handed the resulting managed entity to the service layer. That single
 * 22-line class was the most pervasive coupling in the codebase: it meant
 * booking and payment logic expected a live {@code User} object, and it would
 * have become a synchronous call to account-service from all four other services.
 *
 * <p>The claims were already there and already correct - the monolith wrote
 * {@code role} and {@code userId} into the token and then ignored both.
 *
 * <p>The trade is real and deliberate: authorization is now as stale as the
 * token. Disabling an account no longer takes effect until it expires, which is
 * why {@code rentez.jwt.expiration-ms} is 15 minutes here rather than the
 * monolith's 24 hours.
 */
public record CurrentUser(Long id, String email, Role role) {

	public static CurrentUser from(Jwt jwt) {
		// Small integral claims deserialise as Integer, larger ones as Long, so
		// go through Number rather than casting to either.
		Number userId = jwt.getClaim("userId");
		return new CurrentUser(
				userId == null ? null : userId.longValue(),
				jwt.getSubject(),
				Role.valueOf(jwt.getClaimAsString("role")));
	}
}

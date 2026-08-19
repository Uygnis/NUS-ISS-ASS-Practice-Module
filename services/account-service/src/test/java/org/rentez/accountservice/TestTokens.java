package org.rentez.accountservice;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Mints real HS256 tokens with the same default secret the service decodes with.
 *
 * <p>Deliberately not {@code SecurityMockMvcRequestPostProcessors.jwt()}, which
 * injects an already-authenticated token and skips the decoder entirely. The
 * whole architecture rests on the claim that a token account-service issues is
 * accepted by every other service without any of them calling back, so the
 * tokens here are built exactly as {@code JwtIssuer} builds them - same claim
 * names, same algorithm, same shared secret. If that contract ever drifts, these
 * tests stop compiling or start returning 401.
 */
final class TestTokens {

	/** Matches the {@code rentez.jwt.secret} default in application.properties. */
	private static final String SECRET = "demo-only-secret-key-change-me-please-32bytes-min";

	private static final JwtEncoder ENCODER = new NimbusJwtEncoder(
			new ImmutableSecret<>(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256")));

	private TestTokens() {
	}

	static String of(String email, String role, long userId) {
		Instant now = Instant.now();
		JwtClaimsSet claims = JwtClaimsSet.builder()
				.subject(email)
				.issuedAt(now)
				.expiresAt(now.plus(15, ChronoUnit.MINUTES))
				.claim("role", role)
				.claim("userId", userId)
				.build();
		return ENCODER.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
				.getTokenValue();
	}

	static String admin() {
		return of("admin@nusiss.edu", "ADMIN", 1L);
	}

	static String staff() {
		return of("staff@nusiss.edu", "STAFF", 2L);
	}

	static String customer() {
		return of("customer@nusiss.edu", "CUSTOMER", 3L);
	}

	/** The role reservation and payment present on service-to-service calls. */
	static String service() {
		return of("reservation-service", "SERVICE", 0L);
	}

	@SuppressWarnings("unused")
	static SecretKey key() {
		return new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
	}
}

package org.rentez.accountservice.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.rentez.accountservice.domain.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

/**
 * Issues the JWTs the whole platform runs on. account-service is the only issuer;
 * the other four services only ever decode.
 *
 * <p>Uses Spring Security's own Nimbus encoder rather than the monolith's
 * hand-rolled {@code JwtService} and its separate jjwt dependency. Issuing and
 * validating now go through one library configured from one secret, so the two
 * halves cannot drift apart - and the jjwt dependency disappears entirely.
 *
 * <p>The claim set is deliberately unchanged from the monolith
 * ({@code sub} = email, plus {@code role} and {@code userId}), because those
 * claims are what let every other service authorize locally.
 */
@Service
public class JwtIssuer {

	private final JwtEncoder encoder;
	private final long expirationMs;

	public JwtIssuer(@Value("${rentez.jwt.secret}") String secret,
			@Value("${rentez.jwt.expiration-ms}") long expirationMs) {
		// HS256 requires at least a 256-bit key; the configured secret is
		// documented as needing 32+ characters for exactly this reason.
		SecretKey key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
		this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
		this.expirationMs = expirationMs;
	}

	public String issue(User user) {
		Instant now = Instant.now();
		JwtClaimsSet claims = JwtClaimsSet.builder()
				.subject(user.getEmail())
				.issuedAt(now)
				.expiresAt(now.plusMillis(expirationMs))
				.claim("role", user.getRole().name())
				.claim("userId", user.getId())
				.build();
		return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
				.getTokenValue();
	}
}

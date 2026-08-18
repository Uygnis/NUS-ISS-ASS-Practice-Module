package org.rentez.paymentservice.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Mints the short-lived {@code SERVICE}-role token used on outbound calls to
 * other services' {@code /internal} endpoints.
 *
 * <p>Those endpoints are protected twice: nginx refuses any {@code internal}
 * path from outside, and the services themselves require this role. The second
 * check is the one that matters, because docker-compose publishes 8081-8085 on
 * the host - anything on the same machine can reach a service directly and never
 * pass through the gateway.
 *
 * <p>Note that a SERVICE token carries no user identity. It authorizes the
 * <em>caller</em>, not the person behind the request; a booking's ownership is
 * always taken from the customer's own token on the public endpoint.
 *
 * <p>Two minutes is deliberately short. These tokens are minted per call and
 * never leave the internal network, so there is no reason to make one worth
 * stealing.
 */
@Component
public class ServiceTokenProvider {

	private static final long TTL_SECONDS = 120;

	private final JwtEncoder encoder;
	private final String serviceName;

	public ServiceTokenProvider(@Value("${rentez.jwt.secret}") String secret,
			@Value("${spring.application.name}") String serviceName) {
		SecretKey key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
		this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
		this.serviceName = serviceName;
	}

	public String token() {
		Instant now = Instant.now();
		JwtClaimsSet claims = JwtClaimsSet.builder()
				.subject(serviceName)
				.issuedAt(now)
				.expiresAt(now.plus(TTL_SECONDS, ChronoUnit.SECONDS))
				.claim("role", "SERVICE")
				.build();
		return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
				.getTokenValue();
	}
}

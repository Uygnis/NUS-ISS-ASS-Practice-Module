package org.rentez.accountservice.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/**
 * Stateless JWT security.
 *
 * <p>{@code @EnableMethodSecurity} is load-bearing, not decoration: without it
 * every {@code @PreAuthorize} ported from the monolith silently becomes a no-op
 * and all admin endpoints open up to any authenticated caller. It fails silently,
 * which is the worst way for an authorization control to fail.
 *
 * <p>Note what is <em>absent</em> compared to the monolith: no
 * {@code UserDetailsService}, no {@code DaoAuthenticationProvider}, no
 * {@code AuthenticationManager}. Login verifies the password directly in
 * {@code AuthService}, and every other request is authorized from token claims,
 * so nothing reads the user table to authenticate.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

	/**
	 * Work factor 12, matching docs/ch02 and the seed data comments. The monolith
	 * used the default of 10; there are no existing hashes to stay compatible
	 * with, so this is a free upgrade.
	 */
	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder(12);
	}

	@Bean
	public JwtDecoder jwtDecoder(@Value("${rentez.jwt.secret}") String secret) {
		SecretKey key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
		return NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
	}

	/**
	 * Maps the single-valued {@code role} claim onto Spring's authority model.
	 * The converter splits a String claim on whitespace, so {@code role: "ADMIN"}
	 * becomes one {@code ROLE_ADMIN} authority - which is exactly what the
	 * ported {@code hasRole('ADMIN')} expressions expect.
	 */
	@Bean
	public JwtAuthenticationConverter jwtAuthenticationConverter() {
		JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
		authorities.setAuthoritiesClaimName("role");
		authorities.setAuthorityPrefix("ROLE_");

		JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
		converter.setJwtGrantedAuthoritiesConverter(authorities);
		return converter;
	}

	@Bean
	public SecurityFilterChain filterChain(HttpSecurity http, JwtAuthenticationConverter converter) throws Exception {
		http
				.csrf(csrf -> csrf.disable())
				.sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(auth -> auth
						.requestMatchers("/api/accounts/auth/**").permitAll()
						.requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
						// Second half of the /internal control. The gateway already
						// 404s these, but compose publishes 8081-8085 on the host, so
						// anything running locally can reach a service directly.
						.requestMatchers("/api/accounts/internal/**").hasRole("SERVICE")
						.anyRequest().authenticated())
				.oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(converter)));

		return http.build();
	}
}

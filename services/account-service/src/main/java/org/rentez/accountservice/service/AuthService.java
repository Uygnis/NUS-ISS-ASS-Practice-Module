package org.rentez.accountservice.service;

import org.rentez.accountservice.domain.Role;
import org.rentez.accountservice.domain.User;
import org.rentez.accountservice.error.ApiException;
import org.rentez.accountservice.repository.UserRepository;
import org.rentez.accountservice.security.JwtIssuer;
import org.rentez.accountservice.web.dto.AuthResponse;
import org.rentez.accountservice.web.dto.LoginRequest;
import org.rentez.accountservice.web.dto.RegisterRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Registration and login - the only two endpoints on this service that are reachable without a token. */
@Service
public class AuthService {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtIssuer jwtIssuer;
	private final AuditService auditService;

	public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
			JwtIssuer jwtIssuer, AuditService auditService) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.jwtIssuer = jwtIssuer;
		this.auditService = auditService;
	}

	/** Public self-registration always creates a CUSTOMER; staff and admin accounts are provisioned by an admin. */
	@Transactional
	public AuthResponse register(RegisterRequest request) {
		String email = request.email().toLowerCase();
		if (userRepository.existsByEmail(email)) {
			throw new ApiException(HttpStatus.CONFLICT, "An account with this email already exists");
		}
		User user = userRepository.save(new User(
				request.fullName(), email, passwordEncoder.encode(request.password()),
				request.phone(), Role.CUSTOMER));

		auditService.log(user.getEmail(), "REGISTER", "User", user.getId(), "Self-registered as CUSTOMER");
		return AuthResponse.of(jwtIssuer.issue(user), user);
	}

	/**
	 * Verifies the password directly instead of going through an
	 * {@code AuthenticationManager}.
	 *
	 * <p>The monolith delegated to a {@code DaoAuthenticationProvider} backed by a
	 * {@code UserDetailsService}, which also applied the {@code enabled} check as
	 * a side effect. Both of those existed only to serve this one call, and both
	 * would sit awkwardly beside a resource-server filter chain, so they are gone
	 * - which means the disabled-account check has to be explicit here. Losing it
	 * silently would turn "disable this account" into a no-op.
	 *
	 * <p>The same {@link ApiException} is thrown for an unknown email and a wrong
	 * password, so the response cannot be used to enumerate accounts.
	 */
	@Transactional
	public AuthResponse login(LoginRequest request) {
		User user = userRepository.findByEmail(request.email().toLowerCase())
				.orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid email or password"));

		if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
			throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
		}
		if (!user.isEnabled()) {
			throw new ApiException(HttpStatus.FORBIDDEN, "This account has been disabled");
		}

		auditService.log(user.getEmail(), "LOGIN", "User", user.getId(), null);
		return AuthResponse.of(jwtIssuer.issue(user), user);
	}
}

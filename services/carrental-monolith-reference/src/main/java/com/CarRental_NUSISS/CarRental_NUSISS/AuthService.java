package com.CarRental_NUSISS.CarRental_NUSISS;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/** Registration and login. Login delegates credential checking to Spring Security's AuthenticationManager. */
@Service
public class AuthService {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final AuthenticationManager authenticationManager;
	private final JwtService jwtService;
	private final AuditService auditService;

	public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
			AuthenticationManager authenticationManager, JwtService jwtService, AuditService auditService) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.authenticationManager = authenticationManager;
		this.jwtService = jwtService;
		this.auditService = auditService;
	}

	/** Public self-registration always creates a CUSTOMER; staff/admin accounts are provisioned by an admin. */
	public AuthResponse register(RegisterRequest request) {
		if (userRepository.existsByEmail(request.email())) {
			throw new ApiException(HttpStatus.CONFLICT, "An account with this email already exists");
		}
		User user = new User(request.fullName(), request.email(),
				passwordEncoder.encode(request.password()), request.phone(), User.Role.CUSTOMER);
		user = userRepository.save(user);
		auditService.log(user.getEmail(), "REGISTER", "User", user.getId(), "Self-registered as CUSTOMER");
		return new AuthResponse(jwtService.generateToken(user), user.getId(), user.getFullName(), user.getRole());
	}

	public AuthResponse login(LoginRequest request) {
		authenticationManager.authenticate(
				new UsernamePasswordAuthenticationToken(request.email(), request.password()));

		User user = userRepository.findByEmail(request.email())
				.orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid email or password"));

		auditService.log(user.getEmail(), "LOGIN", "User", user.getId(), null);
		return new AuthResponse(jwtService.generateToken(user), user.getId(), user.getFullName(), user.getRole());
	}
}

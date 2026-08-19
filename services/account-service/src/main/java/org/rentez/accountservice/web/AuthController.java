package org.rentez.accountservice.web;

import jakarta.validation.Valid;
import org.rentez.accountservice.service.AuthService;
import org.rentez.accountservice.web.dto.AuthResponse;
import org.rentez.accountservice.web.dto.LoginRequest;
import org.rentez.accountservice.web.dto.RegisterRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Was {@code /api/auth} in the monolith.
 *
 * <p>The gateway proxies with {@code proxy_pass $upstream$request_uri} and does
 * no prefix stripping, so the mapping here <em>is</em> the public path - it has
 * to start with the {@code /api/accounts} prefix nginx routes on, or the request
 * never arrives.
 */
@RestController
@RequestMapping("/api/accounts/auth")
public class AuthController {

	private final AuthService authService;

	public AuthController(AuthService authService) {
		this.authService = authService;
	}

	@PostMapping("/register")
	public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
	}

	@PostMapping("/login")
	public AuthResponse login(@Valid @RequestBody LoginRequest request) {
		return authService.login(request);
	}
}

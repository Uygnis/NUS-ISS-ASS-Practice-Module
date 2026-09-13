package com.CarRental_NUSISS.CarRental_NUSISS;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** Resolves the User entity behind the currently-authenticated request (set by JwtAuthFilter). */
@Component
public class CurrentUserProvider {

	private final UserRepository userRepository;

	public CurrentUserProvider(UserRepository userRepository) {
		this.userRepository = userRepository;
	}

	public User get() {
		String email = SecurityContextHolder.getContext().getAuthentication().getName();
		return userRepository.findByEmail(email)
				.orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Not authenticated"));
	}
}

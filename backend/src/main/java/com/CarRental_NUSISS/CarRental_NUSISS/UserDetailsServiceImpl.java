package com.CarRental_NUSISS.CarRental_NUSISS;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import java.util.List;

/** Loads a User by email and wraps it as Spring Security's UserDetails, with a ROLE_x authority. */
@Service
public class UserDetailsServiceImpl implements UserDetailsService {

	private final UserRepository userRepository;

	public UserDetailsServiceImpl(UserRepository userRepository) {
		this.userRepository = userRepository;
	}

	@Override
	public UserDetails loadUserByUsername(String email) {
		User user = userRepository.findByEmail(email)
				.orElseThrow(() -> new UsernameNotFoundException("No account with email " + email));

		return org.springframework.security.core.userdetails.User
				.withUsername(user.getEmail())
				.password(user.getPasswordHash())
				.disabled(!user.isEnabled())
				.authorities(List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())))
				.build();
	}
}

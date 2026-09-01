package com.CarRental_NUSISS.CarRental_NUSISS;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
public class UserController {

	private final UserService userService;
	private final CurrentUserProvider currentUserProvider;

	public UserController(UserService userService, CurrentUserProvider currentUserProvider) {
		this.userService = userService;
		this.currentUserProvider = currentUserProvider;
	}

	/** GET /api/users/me - the logged-in user's own profile. */
	@GetMapping("/api/users/me")
	public User me() {
		return currentUserProvider.get();
	}

	/** PUT /api/users/me - update the logged-in user's own profile. */
	@PutMapping("/api/users/me")
	public User updateMe(@Valid @RequestBody UpdateProfileRequest request) {
		return userService.updateProfile(currentUserProvider.get(), request);
	}

	@GetMapping("/api/admin/users")
	@PreAuthorize("hasRole('ADMIN')")
	public List<User> listUsers() {
		return userService.listAll();
	}

	@PutMapping("/api/admin/users/{id}/status")
	@PreAuthorize("hasRole('ADMIN')")
	public User setStatus(@PathVariable Long id, @RequestParam boolean enabled) {
		return userService.setEnabled(id, enabled, currentUserProvider.get().getEmail());
	}

	@PutMapping("/api/admin/users/{id}/role")
	@PreAuthorize("hasRole('ADMIN')")
	public User setRole(@PathVariable Long id, @RequestParam User.Role role) {
		return userService.setRole(id, role, currentUserProvider.get().getEmail());
	}
}

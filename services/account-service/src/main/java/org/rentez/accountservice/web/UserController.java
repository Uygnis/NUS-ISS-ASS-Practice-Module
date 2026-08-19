package org.rentez.accountservice.web;

import jakarta.validation.Valid;
import org.rentez.accountservice.security.CurrentUser;
import org.rentez.accountservice.service.UserService;
import org.rentez.accountservice.web.dto.UpdateProfileRequest;
import org.rentez.accountservice.web.dto.UserResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The caller's own profile. Was split across a bare {@code /api/users/me} and
 * {@code /api/admin/users} on one un-mapped controller in the monolith; admin
 * operations now live in {@link AdminUserController}.
 *
 * <p>Every method takes the caller from the verified token via
 * {@link CurrentUser}, so there is no {@code CurrentUserProvider} and no
 * per-request lookup just to discover who is asking.
 */
@RestController
@RequestMapping("/api/accounts/users")
public class UserController {

	private final UserService userService;

	public UserController(UserService userService) {
		this.userService = userService;
	}

	@GetMapping("/me")
	public UserResponse me(@AuthenticationPrincipal Jwt jwt) {
		return userService.getById(CurrentUser.from(jwt).id());
	}

	@PutMapping("/me")
	public UserResponse updateMe(@AuthenticationPrincipal Jwt jwt,
			@Valid @RequestBody UpdateProfileRequest request) {
		return userService.updateProfile(CurrentUser.from(jwt).id(), request);
	}
}

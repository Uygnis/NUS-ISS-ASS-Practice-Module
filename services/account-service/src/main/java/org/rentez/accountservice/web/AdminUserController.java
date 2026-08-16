package org.rentez.accountservice.web;

import org.rentez.accountservice.domain.Role;
import org.rentez.accountservice.security.CurrentUser;
import org.rentez.accountservice.service.ReportingService;
import org.rentez.accountservice.service.UserService;
import org.rentez.accountservice.web.dto.AuditLogResponse;
import org.rentez.accountservice.web.dto.ReportSummary;
import org.rentez.accountservice.web.dto.UserResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Admin account management and this service's audit trail.
 *
 * <p>The class-level {@code @PreAuthorize} only works because
 * {@code SecurityConfig} is annotated {@code @EnableMethodSecurity}. Without it
 * these annotations are silently ignored and every endpoint below is reachable
 * by any authenticated customer.
 */
@RestController
@RequestMapping("/api/accounts/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

	private final UserService userService;
	private final ReportingService reportingService;

	public AdminUserController(UserService userService, ReportingService reportingService) {
		this.userService = userService;
		this.reportingService = reportingService;
	}

	@GetMapping("/users")
	public List<UserResponse> listUsers() {
		return userService.listAll();
	}

	@PutMapping("/users/{id}/status")
	public UserResponse setStatus(@AuthenticationPrincipal Jwt jwt,
			@PathVariable Long id, @RequestParam boolean enabled) {
		return userService.setEnabled(id, enabled, CurrentUser.from(jwt).email());
	}

	@PutMapping("/users/{id}/role")
	public UserResponse setRole(@AuthenticationPrincipal Jwt jwt,
			@PathVariable Long id, @RequestParam Role role) {
		return userService.setRole(id, role, CurrentUser.from(jwt).email());
	}

	/** This service's own trail only - see {@link AuditLogResponse} for why there is no global feed. */
	@GetMapping("/audit-log")
	public List<AuditLogResponse> auditLog(@RequestParam(defaultValue = "100") int limit) {
		return userService.recentAuditLog(limit);
	}

	/**
	 * Was {@code GET /api/admin/reports/summary}, and returns the same numbers.
	 *
	 * <p>Composed from catalog, reservation and payment rather than computed here.
	 * Degrades rather than fails: a section whose service did not answer comes
	 * back null with {@code partial: true}.
	 */
	@GetMapping("/reports/summary")
	public ReportSummary reportSummary() {
		return reportingService.summary();
	}
}

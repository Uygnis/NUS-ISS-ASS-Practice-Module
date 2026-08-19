package org.rentez.catalogservice.web;

import jakarta.validation.Valid;
import org.rentez.catalogservice.domain.MaintenanceStatus;
import org.rentez.catalogservice.security.CurrentUser;
import org.rentez.catalogservice.service.MaintenanceService;
import org.rentez.catalogservice.web.dto.MaintenanceRequest;
import org.rentez.catalogservice.web.dto.MaintenanceResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Was {@code /api/maintenance}; now nested under the catalog prefix, since maintenance is a fleet concern. */
@RestController
@RequestMapping("/api/catalog/maintenance")
public class MaintenanceController {

	private final MaintenanceService maintenanceService;

	public MaintenanceController(MaintenanceService maintenanceService) {
		this.maintenanceService = maintenanceService;
	}

	@PostMapping
	@PreAuthorize("hasRole('ADMIN')")
	@ResponseStatus(HttpStatus.CREATED)
	public MaintenanceResponse schedule(@AuthenticationPrincipal Jwt jwt,
			@Valid @RequestBody MaintenanceRequest request) {
		return maintenanceService.schedule(request, CurrentUser.from(jwt).email());
	}

	@PutMapping("/{id}/status")
	@PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
	public MaintenanceResponse updateStatus(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
			@RequestParam MaintenanceStatus status) {
		return maintenanceService.updateStatus(id, status, CurrentUser.from(jwt).email());
	}

	@GetMapping("/car/{carId}")
	@PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
	public List<MaintenanceResponse> historyFor(@PathVariable Long carId) {
		return maintenanceService.historyFor(carId);
	}
}

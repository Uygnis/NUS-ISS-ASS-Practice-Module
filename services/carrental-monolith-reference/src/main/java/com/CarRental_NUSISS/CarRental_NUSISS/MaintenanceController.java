package com.CarRental_NUSISS.CarRental_NUSISS;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/maintenance")
public class MaintenanceController {

	private final MaintenanceService maintenanceService;
	private final CurrentUserProvider currentUserProvider;

	public MaintenanceController(MaintenanceService maintenanceService, CurrentUserProvider currentUserProvider) {
		this.maintenanceService = maintenanceService;
		this.currentUserProvider = currentUserProvider;
	}

	/** POST /api/maintenance - admin schedules a job; car is pulled from availability immediately. */
	@PostMapping
	@PreAuthorize("hasRole('ADMIN')")
	@ResponseStatus(HttpStatus.CREATED)
	public MaintenanceRecord schedule(@Valid @RequestBody MaintenanceRequest request) {
		return maintenanceService.schedule(request, currentUserProvider.get().getEmail());
	}

	/** PUT /api/maintenance/{id}/status?status=IN_PROGRESS|COMPLETED - staff updates progress. */
	@PutMapping("/{id}/status")
	@PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
	public MaintenanceRecord updateStatus(@PathVariable Long id, @RequestParam MaintenanceRecord.Status status) {
		return maintenanceService.updateStatus(id, status, currentUserProvider.get().getEmail());
	}

	@GetMapping("/car/{carId}")
	@PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
	public List<MaintenanceRecord> historyFor(@PathVariable Long carId) {
		return maintenanceService.historyFor(carId);
	}
}

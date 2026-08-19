package com.CarRental_NUSISS.CarRental_NUSISS;

import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.List;

/**
 * Browsing/search is public (no login needed to window-shop). Everything that
 * changes the fleet is ADMIN-only; STAFF can only flip a car's status.
 */
@RestController
@RequestMapping("/api/cars")
public class CarController {

	private final CarService carService;
	private final CurrentUserProvider currentUserProvider;

	public CarController(CarService carService, CurrentUserProvider currentUserProvider) {
		this.carService = carService;
		this.currentUserProvider = currentUserProvider;
	}

	/** GET /api/cars?location=..&type=..&startDate=..&endDate=.. - browse or search, all filters optional. */
	@GetMapping
	public List<Car> search(
			@RequestParam(required = false) String location,
			@RequestParam(required = false) Car.CarType type,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
		if (location == null && type == null && startDate == null && endDate == null) {
			return carService.browseAvailable();
		}
		return carService.search(location, type, startDate, endDate);
	}

	@GetMapping("/{id}")
	public Car findById(@PathVariable Long id) {
		return carService.getById(id);
	}

	@PostMapping
	@PreAuthorize("hasRole('ADMIN')")
	@ResponseStatus(HttpStatus.CREATED)
	public Car create(@Valid @RequestBody CarRequest request) {
		return carService.create(request, currentUserProvider.get().getEmail());
	}

	@PutMapping("/{id}")
	@PreAuthorize("hasRole('ADMIN')")
	public Car update(@PathVariable Long id, @Valid @RequestBody CarRequest request) {
		return carService.update(id, request, currentUserProvider.get().getEmail());
	}

	@DeleteMapping("/{id}")
	@PreAuthorize("hasRole('ADMIN')")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable Long id) {
		carService.delete(id, currentUserProvider.get().getEmail());
	}

	/** PATCH /api/cars/{id}/status?status=AVAILABLE|MAINTENANCE|RETIRED|RENTED - ADMIN or STAFF. */
	@PatchMapping("/{id}/status")
	@PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
	public Car setStatus(@PathVariable Long id, @RequestParam Car.CarStatus status) {
		return carService.setStatus(id, status, currentUserProvider.get().getEmail());
	}
}

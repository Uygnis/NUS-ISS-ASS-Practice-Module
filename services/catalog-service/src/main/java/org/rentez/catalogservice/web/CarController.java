package org.rentez.catalogservice.web;

import jakarta.validation.Valid;
import org.rentez.catalogservice.domain.CarStatus;
import org.rentez.catalogservice.domain.CarType;
import org.rentez.catalogservice.security.CurrentUser;
import org.rentez.catalogservice.service.CarService;
import org.rentez.catalogservice.web.dto.CarRequest;
import org.rentez.catalogservice.web.dto.CarResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Was {@code /api/cars} in the monolith. The gateway does no prefix stripping, so
 * this mapping is the public path.
 *
 * <p>Browsing stays public, as it was. Everything that changes the fleet is
 * ADMIN-only; STAFF can only flip a car's status, and only between AVAILABLE and
 * MAINTENANCE - a restriction the monolith documented but never enforced.
 *
 * <p>Note the missing {@code startDate}/{@code endDate} parameters: searching by
 * date range needs booking data and now lives at
 * {@code GET /api/reservations/availability}.
 */
@RestController
@RequestMapping("/api/catalog/cars")
public class CarController {

	private final CarService carService;

	public CarController(CarService carService) {
		this.carService = carService;
	}

	@GetMapping
	public List<CarResponse> search(
			@RequestParam(required = false) String location,
			@RequestParam(required = false) CarType type) {
		if (location == null && type == null) {
			return carService.browseAvailable();
		}
		return carService.search(location, type);
	}

	@GetMapping("/{id}")
	public CarResponse findById(@PathVariable Long id) {
		return carService.getById(id);
	}

	@PostMapping
	@PreAuthorize("hasRole('ADMIN')")
	@ResponseStatus(HttpStatus.CREATED)
	public CarResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CarRequest request) {
		return carService.create(request, CurrentUser.from(jwt).email());
	}

	@PutMapping("/{id}")
	@PreAuthorize("hasRole('ADMIN')")
	public CarResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
			@Valid @RequestBody CarRequest request) {
		return carService.update(id, request, CurrentUser.from(jwt).email());
	}

	@DeleteMapping("/{id}")
	@PreAuthorize("hasRole('ADMIN')")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
		carService.delete(id, CurrentUser.from(jwt).email());
	}

	@PatchMapping("/{id}/status")
	@PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
	public CarResponse setStatus(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
			@RequestParam CarStatus status) {
		boolean isAdmin = "ADMIN".equals(CurrentUser.from(jwt).role());
		return carService.setStatus(id, status, CurrentUser.from(jwt).email(), isAdmin);
	}
}

package org.rentez.catalogservice.web;

import org.rentez.catalogservice.domain.CarType;
import org.rentez.catalogservice.service.CarService;
import org.rentez.catalogservice.web.dto.CatalogStats;
import org.rentez.catalogservice.web.dto.InternalCarView;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Service-to-service endpoints. Not part of the public API.
 *
 * <p>Guarded twice, on purpose. nginx returns 404 for any path containing an
 * {@code internal} segment under one of the five API prefixes, but
 * docker-compose publishes 8081-8085 on the host, so anything running on the
 * same machine can reach this service directly and never touch the gateway. The
 * {@code SERVICE} role is the half that survives that, and it validates with the
 * same decoder as every other token.
 */
@RestController
@RequestMapping("/api/catalog/internal")
@PreAuthorize("hasRole('STAFF')")
public class InternalCatalogController {

	private final CarService carService;

	public InternalCatalogController(CarService carService) {
		this.carService = carService;
	}

	/**
	 * Reservation calls this before accepting a booking: it needs to know whether
	 * the car can be rented and what the rate is, and then snapshots the answer.
	 */
	@GetMapping("/cars/{id}")
	public InternalCarView car(@PathVariable Long id) {
		return carService.getInternalView(id);
	}

	/**
	 * Rentable cars for a location/type. Reservation uses this as the candidate
	 * set for a date-range availability search, then subtracts what it has booked.
	 */
	@GetMapping("/cars")
	public List<InternalCarView> cars(
			@RequestParam(required = false) String location,
			@RequestParam(required = false) CarType type) {
		return carService.findRentable(location, type);
	}

	/** Catalog's slice of the admin report, aggregated locally. */
	@GetMapping("/stats")
	public CatalogStats stats() {
		return carService.stats();
	}
}

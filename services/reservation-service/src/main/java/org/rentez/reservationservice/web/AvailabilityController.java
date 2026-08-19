package org.rentez.reservationservice.web;

import org.rentez.reservationservice.service.BookingService;
import org.rentez.reservationservice.web.dto.AvailableCarResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * The date-range half of the monolith's {@code GET /api/cars}.
 *
 * <p>It lives in reservation rather than catalog because the filter needs booking
 * data. Leaving it in catalog would have meant catalog querying reservation while
 * reservation already queries catalog - a cycle in which neither service could be
 * built or deployed on its own.
 *
 * <p>Public, matching the monolith, where {@code GET /api/cars/**} was permitAll
 * and you could search for a car before creating an account.
 */
@RestController
@RequestMapping("/api/reservations/availability")
public class AvailabilityController {

	private final BookingService bookingService;

	public AvailabilityController(BookingService bookingService) {
		this.bookingService = bookingService;
	}

	@GetMapping
	public List<AvailableCarResponse> available(
			@RequestParam(required = false) String location,
			@RequestParam(required = false) String type,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
		return bookingService.findAvailable(location, type, startDate, endDate);
	}
}

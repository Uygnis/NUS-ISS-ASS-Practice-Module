package com.CarRental_NUSISS.CarRental_NUSISS;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/bookings")
@PreAuthorize("hasRole('CUSTOMER')")
public class BookingController {

	private final BookingService bookingService;
	private final CurrentUserProvider currentUserProvider;

	public BookingController(BookingService bookingService, CurrentUserProvider currentUserProvider) {
		this.bookingService = bookingService;
		this.currentUserProvider = currentUserProvider;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public Booking create(@Valid @RequestBody BookingRequest request) {
		return bookingService.create(currentUserProvider.get(), request);
	}

	/** GET /api/bookings/me - booking history: past and upcoming rentals. */
	@GetMapping("/me")
	public List<Booking> myHistory() {
		return bookingService.historyFor(currentUserProvider.get().getId());
	}

	@GetMapping("/{id}")
	public Booking findById(@PathVariable Long id) {
		return bookingService.getOwned(currentUserProvider.get(), id);
	}

	@PutMapping("/{id}")
	public Booking modify(@PathVariable Long id, @Valid @RequestBody BookingUpdateRequest request) {
		return bookingService.modify(currentUserProvider.get(), id, request);
	}

	@DeleteMapping("/{id}")
	public Booking cancel(@PathVariable Long id) {
		return bookingService.cancel(currentUserProvider.get(), id);
	}
}

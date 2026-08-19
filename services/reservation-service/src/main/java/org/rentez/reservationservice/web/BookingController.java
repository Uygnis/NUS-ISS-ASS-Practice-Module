package org.rentez.reservationservice.web;

import jakarta.validation.Valid;
import org.rentez.reservationservice.security.CurrentUser;
import org.rentez.reservationservice.service.BookingService;
import org.rentez.reservationservice.web.dto.BookingRequest;
import org.rentez.reservationservice.web.dto.BookingResponse;
import org.rentez.reservationservice.web.dto.BookingUpdateRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Was {@code /api/bookings}. The class-level CUSTOMER restriction is carried over
 * from the monolith unchanged, so who may call these endpoints has not widened.
 *
 * <p>Every method takes the caller's id from the token rather than from a
 * {@code CurrentUserProvider} database lookup, and ownership is checked against
 * the booking's own {@code customer_id} column - no account-service involved.
 */
@RestController
@RequestMapping("/api/reservations/bookings")
@PreAuthorize("hasRole('CUSTOMER')")
public class BookingController {

	private final BookingService bookingService;

	public BookingController(BookingService bookingService) {
		this.bookingService = bookingService;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public BookingResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody BookingRequest request) {
		CurrentUser caller = CurrentUser.from(jwt);
		return bookingService.create(caller.id(), caller.email(), request);
	}

	@GetMapping("/me")
	public List<BookingResponse> myBookings(@AuthenticationPrincipal Jwt jwt) {
		return bookingService.historyFor(CurrentUser.from(jwt).id());
	}

	@GetMapping("/{id}")
	public BookingResponse findById(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
		return bookingService.getOwned(CurrentUser.from(jwt).id(), id);
	}

	@PutMapping("/{id}")
	public BookingResponse modify(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
			@Valid @RequestBody BookingUpdateRequest request) {
		CurrentUser caller = CurrentUser.from(jwt);
		return bookingService.modify(caller.id(), caller.email(), id, request);
	}

	/** Returns the cancelled booking rather than 204, as the monolith did. */
	@DeleteMapping("/{id}")
	public BookingResponse cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
		CurrentUser caller = CurrentUser.from(jwt);
		return bookingService.cancelOwn(caller.id(), caller.email(), id);
	}
}

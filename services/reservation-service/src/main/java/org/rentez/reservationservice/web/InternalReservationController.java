package org.rentez.reservationservice.web;

import org.rentez.reservationservice.security.CurrentUser;
import org.rentez.reservationservice.service.BookingService;
import org.rentez.reservationservice.web.dto.BookingResponse;
import org.rentez.reservationservice.web.dto.ReservationStats;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Service-to-service endpoints, used by payment-service to drive the payment saga.
 *
 * <p>Every write here is <strong>idempotent by contract</strong>, and that is not
 * a nicety. Payment charges a card and then calls {@code confirm}; if that
 * response is lost it must retry, and a second call has to succeed. A 409 on an
 * already-confirmed booking would read as failure and trigger a refund for a
 * booking that is in fact paid.
 *
 * <p>These endpoints replace the monolith's
 * {@code booking.setStatus(CONFIRMED); bookingRepository.save(booking);} inside
 * {@code PaymentService} - one service reaching into another aggregate's table.
 */
@RestController
@RequestMapping("/api/reservations/internal")
@PreAuthorize("hasRole('SERVICE')")
public class InternalReservationController {

	private final BookingService bookingService;

	public InternalReservationController(BookingService bookingService) {
		this.bookingService = bookingService;
	}

	/** Payment reads the booking to learn the amount owed - never trusting a client-supplied total. */
	@GetMapping("/bookings/{id}")
	public BookingResponse booking(@PathVariable Long id) {
		return bookingService.getInternal(id);
	}

	/** PENDING_PAYMENT or MODIFIED to CONFIRMED. Already CONFIRMED is a success. */
	@PostMapping("/bookings/{id}/confirm")
	public BookingResponse confirm(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
		return bookingService.confirm(id, CurrentUser.from(jwt).email());
	}

	/** Compensation for a refunded or abandoned payment. Already CANCELLED is a success. */
	@PostMapping("/bookings/{id}/cancel")
	public BookingResponse cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
		return bookingService.cancelInternal(id, CurrentUser.from(jwt).email());
	}

	/** Reservation's slice of the admin report, aggregated locally. */
	@GetMapping("/stats")
	public ReservationStats stats() {
		return bookingService.stats();
	}
}

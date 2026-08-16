package org.rentez.paymentservice.web;

import jakarta.validation.Valid;
import org.rentez.paymentservice.security.CurrentUser;
import org.rentez.paymentservice.service.PaymentService;
import org.rentez.paymentservice.web.dto.PaymentRequest;
import org.rentez.paymentservice.web.dto.PaymentResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Payment is the one service whose gateway prefix is also its resource name, so
 * these paths are unchanged from the monolith - {@code POST /api/payments} and
 * {@code POST /api/payments/{id}/refund} still work exactly as before.
 */
@RestController
@RequestMapping("/api/payments")
public class PaymentController {

	private final PaymentService paymentService;

	public PaymentController(PaymentService paymentService) {
		this.paymentService = paymentService;
	}

	/**
	 * Pay for a booking awaiting payment.
	 *
	 * <p>{@code Idempotency-Key} is optional but strongly recommended: send one
	 * and a retried request returns the original payment instead of charging
	 * again. Without it a double submission is still caught, but later and less
	 * precisely - by the unique index that permits only one successful payment per
	 * booking.
	 */
	@PostMapping
	@PreAuthorize("hasRole('CUSTOMER')")
	@ResponseStatus(HttpStatus.CREATED)
	public PaymentResponse pay(@AuthenticationPrincipal Jwt jwt,
			@RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
			@Valid @RequestBody PaymentRequest request) {
		CurrentUser caller = CurrentUser.from(jwt);
		return paymentService.pay(caller.id(), caller.email(), idempotencyKey, request);
	}

	@PostMapping("/{id}/refund")
	@PreAuthorize("hasRole('ADMIN')")
	public PaymentResponse refund(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
		return paymentService.refund(id, CurrentUser.from(jwt).email());
	}

	/**
	 * The monolith had {@code PaymentService.historyFor(bookingId)} with no
	 * endpoint exposing it. Given an owner check it is worth having, so it is
	 * wired up here rather than ported as dead code.
	 */
	@GetMapping("/me")
	public List<PaymentResponse> myPayments(@AuthenticationPrincipal Jwt jwt) {
		return paymentService.historyForCustomer(CurrentUser.from(jwt).id());
	}

	@GetMapping
	@PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
	public List<PaymentResponse> forBooking(@RequestParam Long bookingId) {
		return paymentService.historyForBooking(bookingId);
	}
}

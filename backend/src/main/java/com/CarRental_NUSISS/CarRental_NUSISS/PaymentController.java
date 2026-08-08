package com.CarRental_NUSISS.CarRental_NUSISS;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

	private final PaymentService paymentService;
	private final CurrentUserProvider currentUserProvider;

	public PaymentController(PaymentService paymentService, CurrentUserProvider currentUserProvider) {
		this.paymentService = paymentService;
		this.currentUserProvider = currentUserProvider;
	}

	/** POST /api/payments - customer pays for a PENDING_PAYMENT booking; confirms it on success. */
	@PostMapping
	@PreAuthorize("hasRole('CUSTOMER')")
	@ResponseStatus(HttpStatus.CREATED)
	public Payment pay(@Valid @RequestBody PaymentRequest request) {
		return paymentService.pay(currentUserProvider.get(), request);
	}

	@PostMapping("/{id}/refund")
	@PreAuthorize("hasRole('ADMIN')")
	public Payment refund(@PathVariable Long id) {
		return paymentService.refund(id, currentUserProvider.get().getEmail());
	}
}

package org.rentez.paymentservice.web;

import org.rentez.paymentservice.service.PaymentService;
import org.rentez.paymentservice.web.dto.PaymentStats;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Payment's slice of the admin report, for account-service to compose. */
@RestController
@RequestMapping("/api/payments/internal")
@PreAuthorize("hasRole('SERVICE')")
public class InternalPaymentController {

	private final PaymentService paymentService;

	public InternalPaymentController(PaymentService paymentService) {
		this.paymentService = paymentService;
	}

	@GetMapping("/stats")
	public PaymentStats stats() {
		return paymentService.stats();
	}
}

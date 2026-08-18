package org.rentez.paymentservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * PRD module M4 - Payment and Refund. Owns the {@code rentez_payment} schema.
 *
 * <p>Scheduling is enabled here for two background jobs that the saga depends on:
 * the outbox relay that dispatches notification events, and the reconciliation
 * sweeper that re-drives payments stranded between a successful gateway charge
 * and a failed booking confirmation. Without the sweeper this is not a saga -
 * it is a two-phase write with no recovery path.
 */
@SpringBootApplication
@EnableScheduling
public class PaymentServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(PaymentServiceApplication.class, args);
	}

}

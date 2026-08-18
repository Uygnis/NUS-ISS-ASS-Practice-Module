package org.rentez.reservationservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * PRD module M3 - Booking. Owns the {@code rentez_booking} schema.
 *
 * <p>Scheduling drives the transactional outbox relay, which delivers booking
 * events to notification-service off the request path.
 */
@SpringBootApplication
@EnableScheduling
public class ReservationServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(ReservationServiceApplication.class, args);
	}

}

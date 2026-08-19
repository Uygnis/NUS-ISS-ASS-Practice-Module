package org.rentez.catalogservice.config;

import org.rentez.catalogservice.domain.Car;
import org.rentez.catalogservice.domain.CarType;
import org.rentez.catalogservice.repository.CarRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * The demo fleet, so the catalog is browsable straight after {@code make up}.
 *
 * <p>This is the other half of the monolith's {@code DataSeeder}, which seeded
 * three users <em>and</em> five cars from a single class - a small but exact
 * illustration of the problem being fixed: one component writing to two bounded
 * contexts. The users are seeded by account-service; the cars belong here.
 *
 * <p>Profile-gated and idempotent for the same reason as account's: it ran
 * unconditionally in the monolith, which was safe only because H2 was in-memory.
 * Against MySQL an ungated seeder duplicates the whole fleet on every restart.
 * Run with {@code SPRING_PROFILES_ACTIVE=seed}.
 */
@Component
@Profile("seed")
public class DataSeeder implements CommandLineRunner {

	private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

	private final CarRepository carRepository;

	public DataSeeder(CarRepository carRepository) {
		this.carRepository = carRepository;
	}

	@Override
	public void run(String... args) {
		if (carRepository.count() > 0) {
			log.info("Fleet already seeded ({} cars), skipping", carRepository.count());
			return;
		}

		carRepository.save(new Car("Toyota", "Corolla", 2022, new BigDecimal("65.00"), "Jurong", CarType.SEDAN));
		carRepository.save(new Car("Honda", "Civic", 2023, new BigDecimal("72.50"), "Tampines", CarType.SEDAN));
		carRepository.save(new Car("Mazda", "CX-5", 2021, new BigDecimal("98.00"), "Jurong", CarType.SUV));
		carRepository.save(new Car("Tesla", "Model 3", 2024, new BigDecimal("145.00"), "Changi", CarType.ELECTRIC));
		carRepository.save(new Car("Toyota", "Hiace", 2020, new BigDecimal("110.00"), "Tampines", CarType.TRUCK));

		log.info("Seeded {} demo cars", carRepository.count());
	}
}

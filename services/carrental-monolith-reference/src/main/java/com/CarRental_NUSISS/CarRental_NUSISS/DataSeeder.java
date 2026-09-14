package com.CarRental_NUSISS.CarRental_NUSISS;

import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;

/**
 * Seeds a demo admin/staff/customer account and a small fleet on startup, purely so
 * the API is exercisable immediately. H2 is in-memory, so this runs fresh every boot -
 * remove or gate behind a profile once you move to a persistent database.
 */
@Component
public class DataSeeder implements CommandLineRunner {

	private final UserRepository userRepository;
	private final CarRepository carRepository;
	private final PasswordEncoder passwordEncoder;

	public DataSeeder(UserRepository userRepository, CarRepository carRepository, PasswordEncoder passwordEncoder) {
		this.userRepository = userRepository;
		this.carRepository = carRepository;
		this.passwordEncoder = passwordEncoder;
	}

	@Override
	public void run(String... args) {
		userRepository.save(new User("Ada Admin", "admin@nusiss.edu", passwordEncoder.encode("Admin123!"), "90000001", User.Role.ADMIN));
		userRepository.save(new User("Sam Staff", "staff@nusiss.edu", passwordEncoder.encode("Staff123!"), "90000002", User.Role.STAFF));
		userRepository.save(new User("Cara Customer", "customer@nusiss.edu", passwordEncoder.encode("Customer123!"), "90000003", User.Role.CUSTOMER));

		carRepository.save(new Car("Toyota", "Corolla", 2022, new BigDecimal("65.00"), "Jurong", Car.CarType.SEDAN));
		carRepository.save(new Car("Honda", "Civic", 2023, new BigDecimal("72.50"), "Tampines", Car.CarType.SEDAN));
		carRepository.save(new Car("Mazda", "CX-5", 2021, new BigDecimal("98.00"), "Jurong", Car.CarType.SUV));
		carRepository.save(new Car("Tesla", "Model 3", 2024, new BigDecimal("145.00"), "Changi", Car.CarType.ELECTRIC));
		carRepository.save(new Car("Toyota", "Hiace", 2020, new BigDecimal("110.00"), "Tampines", Car.CarType.TRUCK));

		System.out.println("""

				Seeded demo accounts (password in parentheses):
				  admin@nusiss.edu    (Admin123!)
				  staff@nusiss.edu    (Staff123!)
				  customer@nusiss.edu (Customer123!)
				""");
	}
}

package org.rentez.accountservice.config;

import org.rentez.accountservice.domain.Role;
import org.rentez.accountservice.domain.User;
import org.rentez.accountservice.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Demo accounts, so the API is exercisable straight after {@code make up}.
 *
 * <p>Two changes from the monolith's seeder, both forced by the move off
 * in-memory H2:
 *
 * <ul>
 *   <li><strong>Profile-gated.</strong> It ran unconditionally on every boot.
 *       Against H2 that was harmless because the database vanished on shutdown;
 *       against MySQL the second boot violates {@code uk_app_user_email} and the
 *       application fails to start. Run with {@code SPRING_PROFILES_ACTIVE=seed}.</li>
 *   <li><strong>Users only.</strong> The original seeded three users
 *       <em>and</em> five cars from one class - it spanned two bounded contexts.
 *       The cars belong to catalog-service and are seeded there.</li>
 * </ul>
 *
 * <p>The {@code existsByEmail} guard makes it idempotent even within the profile,
 * so re-running against a populated database is a no-op rather than a crash.
 *
 * <p>These credentials are demo fixtures for local development, never for a
 * deployed environment - which is what the profile gate enforces.
 */
@Component
@Profile("seed")
public class DataSeeder implements CommandLineRunner {

	private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;

	public DataSeeder(UserRepository userRepository, PasswordEncoder passwordEncoder) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
	}

	@Override
	public void run(String... args) {
		seed("Ada Admin", "admin@nusiss.edu", "Admin123!", "90000001", Role.ADMIN);
		seed("Sam Staff", "staff@nusiss.edu", "Staff123!", "90000002", Role.STAFF);
		seed("Cara Customer", "customer@nusiss.edu", "Customer123!", "90000003", Role.CUSTOMER);
	}

	private void seed(String fullName, String email, String rawPassword, String phone, Role role) {
		if (userRepository.existsByEmail(email)) {
			log.info("Seed account {} already present, skipping", email);
			return;
		}
		userRepository.save(new User(fullName, email, passwordEncoder.encode(rawPassword), phone, role));
		log.info("Seeded {} account: {}", role, email);
	}
}

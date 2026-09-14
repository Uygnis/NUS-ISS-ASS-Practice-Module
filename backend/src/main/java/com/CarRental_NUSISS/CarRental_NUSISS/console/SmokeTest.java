package com.CarRental_NUSISS.CarRental_NUSISS.console;

import com.CarRental_NUSISS.CarRental_NUSISS.*;
import jakarta.validation.Validator;
import org.springframework.boot.SpringBootVersion;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Scripted end-to-end drive of every backend service, with assertions. Nothing is mocked:
 * the same beans the controllers use are called in the same order a real client would call
 * them, over the embedded in-memory H2 database.
 *
 * <p>Organised in sections. A failed assertion is recorded and the section carries on; an
 * unexpected exception aborts that one section only, so one broken flow never hides the rest.
 *
 * <p>It writes real rows (a customer, a car, bookings, payments). On the throwaway
 * in-memory database that is the point; if you run it with {@code --with-web} against a
 * persistent database, expect the test data to stick around.
 *
 * <p>Every check is also handed to a {@link TestReport}, which writes the whole run out as
 * HTML and PDF for anyone who would rather read a document than watch a console.
 */
final class SmokeTest {

	private final AuthService authService;
	private final CarService carService;
	private final BookingService bookingService;
	private final PaymentService paymentService;
	private final MaintenanceService maintenanceService;
	private final UserService userService;
	private final ReportService reportService;
	private final UserRepository userRepository;
	private final NotificationRepository notificationRepository;
	private final AuditLogRepository auditLogRepository;
	private final JwtService jwtService;
	private final Validator validator;

	private final List<String> failures = new ArrayList<>();
	private int passed;

	private final TestReport report;
	private final Path reportDir;

	/** Unique per run so the suite can be run repeatedly against the same database. */
	private final String tag = Long.toHexString(System.nanoTime());

	private User customer;
	private User rival;
	private User admin;
	private User staff;
	private Car car;
	private Booking booking;
	private Payment payment;
	private MaintenanceRecord maintenance;

	SmokeTest(ApplicationContext context, boolean webRunning, Path reportDir) {
		this.reportDir = reportDir;
		this.report = new TestReport("CarRental-NUSISS backend - smoke test report",
				describeEnvironment(context.getEnvironment(), webRunning));
		this.authService = context.getBean(AuthService.class);
		this.carService = context.getBean(CarService.class);
		this.bookingService = context.getBean(BookingService.class);
		this.paymentService = context.getBean(PaymentService.class);
		this.maintenanceService = context.getBean(MaintenanceService.class);
		this.userService = context.getBean(UserService.class);
		this.reportService = context.getBean(ReportService.class);
		this.userRepository = context.getBean(UserRepository.class);
		this.notificationRepository = context.getBean(NotificationRepository.class);
		this.auditLogRepository = context.getBean(AuditLogRepository.class);
		this.jwtService = context.getBean(JwtService.class);
		this.validator = context.getBeanProvider(Validator.class).getIfUnique();
	}

	/** Facts about this run, printed at the top of the report so it can stand on its own. */
	private Map<String, String> describeEnvironment(Environment environment, boolean webRunning) {
		String profiles = String.join(", ", environment.getActiveProfiles());
		Map<String, String> facts = new LinkedHashMap<>();
		facts.put("Suite", "console harness, --smoke");
		facts.put("Mode", webRunning
				? "--with-web: full web application, REST API on :8080"
				: "headless: services called directly, no web layer");
		facts.put("Profiles", profiles.isEmpty() ? "(default)" : profiles);
		facts.put("Database", environment.getProperty("spring.datasource.url", "(unknown)"));
		facts.put("Schema", environment.getProperty("spring.jpa.hibernate.ddl-auto", "(default)"));
		facts.put("Spring Boot", SpringBootVersion.getVersion());
		facts.put("Java", System.getProperty("java.version") + " (" + System.getProperty("java.vm.name") + ")");
		facts.put("Run tag", tag);
		return facts;
	}

	/** @return the number of failed checks (0 = everything passed). */
	int run() {
		System.out.println("""

				================================================================
				 Automated end-to-end smoke test
				================================================================""");
		long startedAt = System.nanoTime();

		seededData();
		registrationAndLogin();
		validationRules();
		fleetAdmin();
		bookingLifecycle();
		payments();
		maintenance();
		cancellation();
		userAdministration();
		reportsAndAudit();

		report.finished(Duration.ofNanos(System.nanoTime() - startedAt));

		System.out.printf("%n----------------------------------------------------------------%n");
		System.out.printf(" %d passed, %d failed%n", passed, failures.size());
		failures.forEach(f -> System.out.println("   FAIL " + f));
		writeReport();
		System.out.println("----------------------------------------------------------------");
		return failures.size();
	}

	/**
	 * A report that cannot be written is worth saying out loud, but it is not a test failure -
	 * it must not change the exit code.
	 */
	private void writeReport() {
		try {
			System.out.println();
			for (Path file : report.write(reportDir)) {
				System.out.println(" report  " + file.toAbsolutePath());
			}
		}
		catch (IOException | RuntimeException e) {
			System.out.println();
			System.out.println(" ! could not write the report to " + reportDir.toAbsolutePath()
					+ ": " + describe(e));
		}
	}

	// ---------------------------------------------------------------- sections

	private void seededData() {
		section("Seeded demo data", () -> {
			admin = findUser("admin@nusiss.edu");
			staff = findUser("staff@nusiss.edu");
			User seededCustomer = findUser("customer@nusiss.edu");
			verify("admin account seeded with ADMIN role", admin.getRole() == User.Role.ADMIN, admin.getRole());
			verify("staff account seeded with STAFF role", staff.getRole() == User.Role.STAFF, staff.getRole());
			verify("customer account seeded with CUSTOMER role",
					seededCustomer.getRole() == User.Role.CUSTOMER, seededCustomer.getRole());
			verify("password hashes are BCrypt, not plaintext",
					admin.getPasswordHash().startsWith("$2") && !admin.getPasswordHash().equals("Admin123!"));

			List<Car> available = carService.browseAvailable();
			verify("fleet seeded and browsable", !available.isEmpty(), available.size() + " car(s)");
			verify("every browsable car is AVAILABLE",
					available.stream().allMatch(c -> c.getStatus() == Car.CarStatus.AVAILABLE));
		});
	}

	private void registrationAndLogin() {
		section("Registration and login", () -> {
			String email = "smoke-" + tag + "@test.local";
			AuthResponse registered = authService.register(
					new RegisterRequest("Smoke Customer", email, "Sm0ke!pass", "90001234"));
			verify("self-registration returns a JWT", jwtService.isValid(registered.token()));
			verify("token subject is the registered email",
					email.equals(jwtService.extractEmail(registered.token())), jwtService.extractEmail(registered.token()));
			verify("self-registration forces role CUSTOMER",
					registered.role() == User.Role.CUSTOMER, registered.role());
			customer = findUser(email);
			verify("password was hashed on the way in", !"Sm0ke!pass".equals(customer.getPasswordHash()));

			expectApi("registering the same email twice is a conflict", HttpStatus.CONFLICT,
					() -> authService.register(new RegisterRequest("Impostor", email, "Sm0ke!pass", null)));

			AuthResponse loggedIn = authService.login(new LoginRequest(email, "Sm0ke!pass"));
			verify("login with the right password succeeds", jwtService.isValid(loggedIn.token()));
			verify("login reports the right user", customer.getId().equals(loggedIn.userId()));

			expectAuthFailure("login with the wrong password is rejected",
					() -> authService.login(new LoginRequest(email, "wrong-password")));
			expectAuthFailure("login with an unknown email is rejected",
					() -> authService.login(new LoginRequest("nobody-" + tag + "@test.local", "whatever")));

			verify("seeded admin can log in",
					jwtService.isValid(authService.login(new LoginRequest("admin@nusiss.edu", "Admin123!")).token()));
			verify("seeded staff can log in",
					jwtService.isValid(authService.login(new LoginRequest("staff@nusiss.edu", "Staff123!")).token()));

			// A second customer, used below to prove one customer cannot touch another's booking.
			String rivalEmail = "rival-" + tag + "@test.local";
			authService.register(new RegisterRequest("Rival Customer", rivalEmail, "Riv4l!pass", null));
			rival = findUser(rivalEmail);

			User updated = userService.updateProfile(customer, new UpdateProfileRequest("Smoke Customer II", "90009999"));
			verify("profile update persists", "Smoke Customer II".equals(findUser(email).getFullName()));
			verify("profile update returns the saved user", "90009999".equals(updated.getPhone()));
			customer = findUser(email);
		});
	}

	private void validationRules() {
		section("Bean validation (what @Valid rejects at the controllers)", () -> {
			if (validator == null) {
				verify("a jakarta Validator bean is available", false, "no Validator bean in the context");
				return;
			}
			verify("short password is rejected",
					!validator.validate(new RegisterRequest("A B", "a@b.com", "short", null)).isEmpty());
			verify("malformed email is rejected",
					!validator.validate(new RegisterRequest("A B", "not-an-email", "longenough1", null)).isEmpty());
			verify("blank full name is rejected",
					!validator.validate(new RegisterRequest("  ", "a@b.com", "longenough1", null)).isEmpty());
			verify("a valid registration passes",
					validator.validate(new RegisterRequest("A B", "a@b.com", "longenough1", null)).isEmpty());
			verify("booking in the past is rejected",
					!validator.validate(new BookingRequest(1L, LocalDate.now().minusDays(3),
							LocalDate.now().plusDays(1), null)).isEmpty());
			Set<?> negativeRate = validator.validate(
					new CarRequest("Make", "Model", 2024, new BigDecimal("-5"), "Nowhere", Car.CarType.SEDAN));
			verify("negative daily rate is rejected", !negativeRate.isEmpty());
		});
	}

	private void fleetAdmin() {
		section("Fleet administration", () -> {
			String location = "SmokeVille-" + tag;
			car = carService.create(new CarRequest("Smoke", "Runner", 2024,
					new BigDecimal("50.00"), location, Car.CarType.SUV), admin.getEmail());
			verify("admin can add a car", car.getId() != null);
			verify("a new car starts AVAILABLE", car.getStatus() == Car.CarStatus.AVAILABLE, car.getStatus());

			verify("search by location finds it",
					carService.search(location, null, null, null).stream()
							.anyMatch(c -> c.getId().equals(car.getId())));
			verify("search by location is case-insensitive",
					!carService.search(location.toUpperCase(), null, null, null).isEmpty());
			verify("search by type narrows correctly",
					carService.search(location, Car.CarType.SUV, null, null).size() == 1);
			verify("search by the wrong type excludes it",
					carService.search(location, Car.CarType.LUXURY, null, null).isEmpty());
			expectApi("reversed date range is rejected by search", HttpStatus.BAD_REQUEST,
					() -> carService.search(null, null, LocalDate.now().plusDays(5), LocalDate.now().plusDays(1)));

			Car updated = carService.update(car.getId(), new CarRequest("Smoke", "Runner LX", 2024,
					new BigDecimal("60.00"), location, Car.CarType.SUV), admin.getEmail());
			verify("admin can edit a car", "Runner LX".equals(carService.getById(car.getId()).getModel()));
			verify("edited rate is persisted",
					new BigDecimal("60.00").compareTo(updated.getDailyRate()) == 0, updated.getDailyRate());
			car = carService.getById(car.getId());

			carService.setStatus(car.getId(), Car.CarStatus.RETIRED, staff.getEmail());
			verify("a RETIRED car disappears from browsing",
					carService.browseAvailable().stream().noneMatch(c -> c.getId().equals(car.getId())));
			carService.setStatus(car.getId(), Car.CarStatus.AVAILABLE, staff.getEmail());
			verify("restoring AVAILABLE brings it back",
					carService.browseAvailable().stream().anyMatch(c -> c.getId().equals(car.getId())));

			expectApi("fetching an unknown car id is a 404", HttpStatus.NOT_FOUND,
					() -> carService.getById(999_999L));

			// A car with nothing referencing it, so delete is not blocked by a foreign key.
			Car throwaway = carService.create(new CarRequest("Throw", "Away", 2019,
					new BigDecimal("10.00"), "Nowhere-" + tag, Car.CarType.HATCHBACK), admin.getEmail());
			carService.delete(throwaway.getId(), admin.getEmail());
			expectApi("a deleted car is gone", HttpStatus.NOT_FOUND,
					() -> carService.getById(throwaway.getId()));
		});
	}

	private void bookingLifecycle() {
		section("Booking lifecycle", () -> {
			LocalDate start = LocalDate.now().plusDays(1);
			LocalDate end = start.plusDays(3);
			BigDecimal rate = car.getDailyRate();

			booking = bookingService.create(customer, new BookingRequest(car.getId(), start, end, "Jurong"));
			verify("a new booking is PENDING_PAYMENT",
					booking.getStatus() == Booking.BookingStatus.PENDING_PAYMENT, booking.getStatus());
			verify("price = daily rate x inclusive days (4)",
					rate.multiply(BigDecimal.valueOf(4)).compareTo(booking.getTotalAmount()) == 0,
					booking.getTotalAmount());

			expectApi("double-booking the same car and dates is a conflict", HttpStatus.CONFLICT,
					() -> bookingService.create(rival, new BookingRequest(car.getId(), start.plusDays(1), end, null)));
			verify("a booked car is filtered out of a date search",
					carService.search(car.getLocation(), null, start, end).stream()
							.noneMatch(c -> c.getId().equals(car.getId())));
			verify("the same car is still bookable on free dates",
					carService.search(car.getLocation(), null, end.plusDays(10), end.plusDays(12)).stream()
							.anyMatch(c -> c.getId().equals(car.getId())));

			expectApi("reversed dates are rejected on create", HttpStatus.BAD_REQUEST,
					() -> bookingService.create(customer, new BookingRequest(car.getId(), end, start, null)));
			expectApi("booking an unknown car is a 404", HttpStatus.NOT_FOUND,
					() -> bookingService.create(customer, new BookingRequest(999_999L, start, end, null)));

			expectApi("another customer cannot read your booking", HttpStatus.FORBIDDEN,
					() -> bookingService.getOwned(rival, booking.getId()));
			expectApi("another customer cannot modify your booking", HttpStatus.FORBIDDEN,
					() -> bookingService.modify(rival, booking.getId(),
							new BookingUpdateRequest(start, end, "Hijacked")));

			Booking modified = bookingService.modify(customer, booking.getId(),
					new BookingUpdateRequest(start, start.plusDays(5), "Tampines"));
			verify("modify recalculates the total for 6 days",
					rate.multiply(BigDecimal.valueOf(6)).compareTo(modified.getTotalAmount()) == 0,
					modified.getTotalAmount());
			verify("modify updates the pickup location", "Tampines".equals(modified.getPickupLocation()));
			verify("an unpaid booking stays PENDING_PAYMENT after modify",
					modified.getStatus() == Booking.BookingStatus.PENDING_PAYMENT, modified.getStatus());
			booking = modified;

			verify("booking shows up in the customer's history",
					bookingService.historyFor(customer.getId()).stream()
							.anyMatch(b -> b.getId().equals(booking.getId())));
			verify("it does not show up in another customer's history",
					bookingService.historyFor(rival.getId()).stream()
							.noneMatch(b -> b.getId().equals(booking.getId())));
		});
	}

	private void payments() {
		section("Payments and notifications", () -> {
			BigDecimal revenueBefore = reportService.summary().totalRevenue();

			expectApi("a card starting 0000 is declined", HttpStatus.PAYMENT_REQUIRED,
					() -> paymentService.pay(customer,
							new PaymentRequest(booking.getId(), Payment.Method.CARD, "0000111122223333")));
			verify("the declined attempt is recorded as FAILED",
					paymentService.historyFor(booking.getId()).stream()
							.anyMatch(p -> p.getStatus() == Payment.Status.FAILED));
			verify("a declined payment leaves the booking PENDING_PAYMENT",
					bookingService.getById(booking.getId()).getStatus() == Booking.BookingStatus.PENDING_PAYMENT);

			expectApi("you cannot pay for someone else's booking", HttpStatus.FORBIDDEN,
					() -> paymentService.pay(rival,
							new PaymentRequest(booking.getId(), Payment.Method.CARD, "4111111111111111")));
			expectApi("paying for an unknown booking is a 404", HttpStatus.NOT_FOUND,
					() -> paymentService.pay(customer,
							new PaymentRequest(999_999L, Payment.Method.CARD, "4111111111111111")));

			payment = paymentService.pay(customer,
					new PaymentRequest(booking.getId(), Payment.Method.CARD, "4111111111111111"));
			verify("a good card succeeds", payment.getStatus() == Payment.Status.SUCCESS, payment.getStatus());
			verify("the charge equals the booking total",
					booking.getTotalAmount().compareTo(payment.getAmount()) == 0, payment.getAmount());
			verify("a transaction reference is issued",
					payment.getTransactionRef() != null && payment.getTransactionRef().startsWith("TXN-"));
			verify("paying confirms the booking",
					bookingService.getById(booking.getId()).getStatus() == Booking.BookingStatus.CONFIRMED);
			verify("reported revenue grows by exactly the amount charged",
					revenueBefore.add(payment.getAmount())
							.compareTo(reportService.summary().totalRevenue()) == 0,
					reportService.summary().totalRevenue());

			expectApi("paying twice is a conflict", HttpStatus.CONFLICT,
					() -> paymentService.pay(customer,
							new PaymentRequest(booking.getId(), Payment.Method.WALLET, null)));

			List<String> types = notificationTypes(customer.getId());
			verify("a payment receipt was sent", types.contains("PAYMENT_RECEIPT"), types);
			verify("a booking confirmation was sent", types.contains("BOOKING_CONFIRMED"), types);

			Booking confirmed = bookingService.getById(booking.getId());
			Booking remodified = bookingService.modify(customer, confirmed.getId(),
					new BookingUpdateRequest(confirmed.getStartDate(), confirmed.getEndDate().plusDays(1), "Changi"));
			verify("modifying a CONFIRMED booking flags it MODIFIED",
					remodified.getStatus() == Booking.BookingStatus.MODIFIED, remodified.getStatus());

			Payment refunded = paymentService.refund(payment.getId(), admin.getEmail());
			verify("admin can refund a successful payment",
					refunded.getStatus() == Payment.Status.REFUNDED, refunded.getStatus());
			verify("refunding cancels the booking",
					bookingService.getById(booking.getId()).getStatus() == Booking.BookingStatus.CANCELLED);
			verify("a refund notification was sent",
					notificationTypes(customer.getId()).contains("REFUND_PROCESSED"));
			verify("a refunded payment drops back out of reported revenue",
					revenueBefore.compareTo(reportService.summary().totalRevenue()) == 0,
					reportService.summary().totalRevenue());
			expectApi("refunding twice is a conflict", HttpStatus.CONFLICT,
					() -> paymentService.refund(payment.getId(), admin.getEmail()));
			expectApi("refunding an unknown payment is a 404", HttpStatus.NOT_FOUND,
					() -> paymentService.refund(999_999L, admin.getEmail()));

			verify("cancelling an already-cancelled booking is a no-op",
					bookingService.cancel(customer, booking.getId()).getStatus() == Booking.BookingStatus.CANCELLED);
			verify("payment history lists both the failed and the refunded attempt",
					paymentService.historyFor(booking.getId()).size() >= 2,
					paymentService.historyFor(booking.getId()).size());
		});
	}

	private void maintenance() {
		section("Maintenance", () -> {
			maintenance = maintenanceService.schedule(
					new MaintenanceRequest(car.getId(), "Smoke test service", LocalDate.now()), admin.getEmail());
			verify("scheduling starts in SCHEDULED",
					maintenance.getStatus() == MaintenanceRecord.Status.SCHEDULED, maintenance.getStatus());
			verify("scheduling pulls the car into MAINTENANCE",
					carService.getById(car.getId()).getStatus() == Car.CarStatus.MAINTENANCE);
			verify("a car in MAINTENANCE is not browsable",
					carService.browseAvailable().stream().noneMatch(c -> c.getId().equals(car.getId())));
			expectApi("a car in MAINTENANCE cannot be booked", HttpStatus.CONFLICT,
					() -> bookingService.create(rival, new BookingRequest(car.getId(),
							LocalDate.now().plusDays(30), LocalDate.now().plusDays(31), null)));

			MaintenanceRecord inProgress = maintenanceService.updateStatus(
					maintenance.getId(), MaintenanceRecord.Status.IN_PROGRESS, staff.getEmail());
			verify("staff can move a job to IN_PROGRESS",
					inProgress.getStatus() == MaintenanceRecord.Status.IN_PROGRESS, inProgress.getStatus());
			verify("the car stays in MAINTENANCE while work is in progress",
					carService.getById(car.getId()).getStatus() == Car.CarStatus.MAINTENANCE);

			MaintenanceRecord completed = maintenanceService.updateStatus(
					maintenance.getId(), MaintenanceRecord.Status.COMPLETED, staff.getEmail());
			verify("completion stamps a completed date", completed.getCompletedDate() != null);
			verify("completion returns the car to AVAILABLE",
					carService.getById(car.getId()).getStatus() == Car.CarStatus.AVAILABLE);
			verify("the car is bookable again", carService.browseAvailable().stream()
					.anyMatch(c -> c.getId().equals(car.getId())));

			verify("maintenance history lists the job",
					maintenanceService.historyFor(car.getId()).stream()
							.anyMatch(r -> r.getId().equals(maintenance.getId())));
			expectApi("scheduling for an unknown car is a 404", HttpStatus.NOT_FOUND,
					() -> maintenanceService.schedule(
							new MaintenanceRequest(999_999L, "Nope", LocalDate.now()), admin.getEmail()));
			expectApi("updating an unknown record is a 404", HttpStatus.NOT_FOUND,
					() -> maintenanceService.updateStatus(999_999L,
							MaintenanceRecord.Status.COMPLETED, staff.getEmail()));
		});
	}

	/**
	 * The plain cancel path, which the payment section never reaches: a refund cancels the
	 * booking itself, and {@code BookingService.cancel} short-circuits on an already
	 * cancelled booking.
	 */
	private void cancellation() {
		section("Cancellation", () -> {
			LocalDate start = LocalDate.now().plusDays(40);
			Booking own = bookingService.create(customer,
					new BookingRequest(car.getId(), start, start.plusDays(2), "Jurong"));
			int notificationsBefore = notificationTypes(customer.getId()).size();

			Booking cancelled = bookingService.cancel(customer, own.getId());
			verify("a customer can cancel their own booking",
					cancelled.getStatus() == Booking.BookingStatus.CANCELLED, cancelled.getStatus());
			verify("cancelling sends a notification",
					notificationTypes(customer.getId()).size() > notificationsBefore
							&& notificationTypes(customer.getId()).contains("BOOKING_CANCELLED"));
			verify("cancelling frees the dates again",
					carService.search(car.getLocation(), null, start, start.plusDays(2)).stream()
							.anyMatch(c -> c.getId().equals(car.getId())));
			verify("cancelling twice is idempotent",
					bookingService.cancel(customer, own.getId()).getStatus() == Booking.BookingStatus.CANCELLED);

			Booking other = bookingService.create(customer,
					new BookingRequest(car.getId(), start.plusDays(10), start.plusDays(12), null));
			expectApi("a customer cannot cancel someone else's booking", HttpStatus.FORBIDDEN,
					() -> bookingService.cancel(rival, other.getId()));
			verify("staff can cancel any booking without owning it",
					bookingService.cancel(staff, other.getId()).getStatus() == Booking.BookingStatus.CANCELLED);
			expectApi("cancelling an unknown booking is a 404", HttpStatus.NOT_FOUND,
					() -> bookingService.cancel(admin, 999_999L));
		});
	}

	private void userAdministration() {
		section("User administration", () -> {
			verify("admin can list users", userService.listAll().stream()
					.anyMatch(u -> u.getId().equals(customer.getId())));

			userService.setEnabled(customer.getId(), false, admin.getEmail());
			verify("disabling is persisted", !findUser(customer.getEmail()).isEnabled());
			expectAuthFailure("a disabled account cannot log in",
					() -> authService.login(new LoginRequest(customer.getEmail(), "Sm0ke!pass")));

			userService.setEnabled(customer.getId(), true, admin.getEmail());
			verify("re-enabling restores login", jwtService.isValid(
					authService.login(new LoginRequest(customer.getEmail(), "Sm0ke!pass")).token()));

			userService.setRole(customer.getId(), User.Role.STAFF, admin.getEmail());
			verify("role change is persisted",
					findUser(customer.getEmail()).getRole() == User.Role.STAFF);
			userService.setRole(customer.getId(), User.Role.CUSTOMER, admin.getEmail());
			verify("role change back is persisted",
					findUser(customer.getEmail()).getRole() == User.Role.CUSTOMER);
			customer = findUser(customer.getEmail());

			expectApi("enabling an unknown user is a 404", HttpStatus.NOT_FOUND,
					() -> userService.setEnabled(999_999L, true, admin.getEmail()));
			expectApi("re-roling an unknown user is a 404", HttpStatus.NOT_FOUND,
					() -> userService.setRole(999_999L, User.Role.ADMIN, admin.getEmail()));
		});
	}

	private void reportsAndAudit() {
		section("Reports and audit trail", () -> {
			ReportSummary summary = reportService.summary();
			verify("report counts the fleet", summary.totalCars() > 0, summary.totalCars());
			verify("available + maintenance never exceed the fleet size",
					summary.availableCars() + summary.carsInMaintenance() <= summary.totalCars());
			verify("report counts bookings", summary.totalBookings() > 0, summary.totalBookings());
			verify("report counts the cancellation we made", summary.cancelledBookings() > 0);
			verify("revenue is never negative",
					summary.totalRevenue().compareTo(BigDecimal.ZERO) >= 0, summary.totalRevenue());
			verify("bookings are grouped by car type", !summary.bookingsByCarType().isEmpty(),
					summary.bookingsByCarType());

			List<String> actions = auditLogRepository
					.findAllByOrderByTimestampDesc(PageRequest.of(0, 500))
					.stream().map(AuditLog::getAction).toList();
			for (String expected : List.of("REGISTER", "LOGIN", "CREATE_CAR", "UPDATE_CAR", "DELETE_CAR",
					"SET_CAR_STATUS", "CREATE_BOOKING", "MODIFY_BOOKING", "CANCEL_BOOKING",
					"PAYMENT_FAILED", "PAYMENT_SUCCESS", "REFUND", "SCHEDULE_MAINTENANCE",
					"UPDATE_MAINTENANCE_STATUS", "UPDATE_PROFILE", "DISABLE_USER", "ENABLE_USER",
					"CHANGE_ROLE")) {
				verify("audit trail records " + expected, actions.contains(expected));
			}
		});
	}

	// ---------------------------------------------------------------- assertion plumbing

	private interface Body {
		void run() throws Exception;
	}

	private void section(String title, Body body) {
		System.out.printf("%n-- %s%n", title);
		report.section(title);
		try {
			body.run();
		}
		catch (Throwable t) {
			fail(title + " aborted", describe(t));
		}
	}

	private void verify(String name, boolean condition) {
		verify(name, condition, null);
	}

	private void verify(String name, boolean condition, Object actual) {
		if (condition) {
			pass(name, actual == null ? null : String.valueOf(actual));
		}
		else {
			fail(name, actual == null ? null : "got: " + actual);
		}
	}

	/** Asserts the call fails with exactly the {@link ApiException} status the API would return. */
	private void expectApi(String name, HttpStatus expected, Runnable body) {
		try {
			body.run();
			fail(name, "expected " + expected.value() + " but the call succeeded");
		}
		catch (ApiException e) {
			if (e.getStatus() == expected) {
				pass(name, expected.value() + " \"" + e.getMessage() + "\"");
			}
			else {
				fail(name, "expected " + expected.value() + " but got " + e.getStatus().value()
						+ " \"" + e.getMessage() + "\"");
			}
		}
		catch (Throwable t) {
			fail(name, "expected " + expected.value() + " but got " + describe(t));
		}
	}

	/** Asserts Spring Security's AuthenticationManager rejected the credentials. */
	private void expectAuthFailure(String name, Runnable body) {
		try {
			body.run();
			fail(name, "expected an authentication failure but the call succeeded");
		}
		catch (AuthenticationException e) {
			pass(name, e.getClass().getSimpleName());
		}
		catch (Throwable t) {
			fail(name, "expected an authentication failure but got " + describe(t));
		}
	}

	private void pass(String name) {
		pass(name, null);
	}

	/** {@code detail} is the observed value or status - evidence for the claim in {@code name}. */
	private void pass(String name, String detail) {
		passed++;
		report.record(name, detail, true);
		System.out.println("   ok   " + name + (detail == null ? "" : "  ->  " + detail));
	}

	private void fail(String name, String detail) {
		failures.add(detail == null ? name : name + " (" + detail + ")");
		report.record(name, detail, false);
		System.out.println("   FAIL " + name + (detail == null ? "" : " (" + detail + ")"));
	}

	private String describe(Throwable t) {
		return t.getClass().getSimpleName() + ": " + t.getMessage();
	}

	private User findUser(String email) {
		return userRepository.findByEmail(email)
				.orElseThrow(() -> new AssertionError("no user with email " + email));
	}

	private List<String> notificationTypes(Long userId) {
		return notificationRepository.findByRecipientIdOrderBySentAtDesc(userId)
				.stream().map(Notification::getType).toList();
	}
}

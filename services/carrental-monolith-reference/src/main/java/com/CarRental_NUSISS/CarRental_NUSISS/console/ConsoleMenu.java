package com.CarRental_NUSISS.CarRental_NUSISS.console;

import com.CarRental_NUSISS.CarRental_NUSISS.*;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.context.ApplicationContext;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The text UI. Every option calls the same service bean the matching REST controller
 * calls, with the same arguments, so this is a faithful (HTTP-less) drive of the backend.
 *
 * <p>Two kinds of rules exist in the app and they behave differently here:
 * <ul>
 *   <li><b>Role rules</b> live on the controllers as {@code @PreAuthorize}. Services do not
 *       re-check them, so this menu mirrors them by only offering an option to the roles the
 *       controller allows.</li>
 *   <li><b>Ownership and state rules</b> (booking belongs to you, booking must be
 *       PENDING_PAYMENT, car must be AVAILABLE, ...) live in the services and are enforced
 *       for real - they surface below as {@code [HTTP 403]}, {@code [HTTP 409]} and so on.</li>
 * </ul>
 */
final class ConsoleMenu {

	private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

	private final ApplicationContext context;
	private final Scanner in = new Scanner(System.in);
	private final boolean webRunning;
	/** Where the smoke suite drops its HTML/PDF report. */
	private final Path reportDir;

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
	/** Same bean {@code @Valid} uses on the controllers; null only if validation is absent. */
	private final Validator validator;

	private User currentUser;
	private String currentToken;
	private boolean running = true;

	ConsoleMenu(ApplicationContext context, boolean webRunning, Path reportDir) {
		this.context = context;
		this.webRunning = webRunning;
		this.reportDir = reportDir;
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

	// ---------------------------------------------------------------- entry points

	/** Interactive menu loop. Always returns 0 - the exit code only matters for --smoke. */
	int runInteractive() {
		banner();
		while (running) {
			Map<String, MenuItem> items = printMenu();
			try {
				String choice = readLine("Choice").trim();
				if (choice.isEmpty()) {
					continue;
				}
				MenuItem item = items.get(choice);
				if (item == null) {
					System.out.println("  ! No such option: " + choice);
					continue;
				}
				System.out.println();
				item.action().run();
			}
			catch (EndOfInput e) {
				System.out.println("\n(input stream closed - exiting)");
				return 0;
			}
			catch (Cancelled e) {
				System.out.println("  (cancelled)");
			}
			catch (ApiException e) {
				System.out.printf("  ! [HTTP %d %s] %s%n",
						e.getStatus().value(), e.getStatus().getReasonPhrase(), e.getMessage());
			}
			catch (AuthenticationException e) {
				System.out.printf("  ! [HTTP 401 Unauthorized] %s%n", e.getMessage());
			}
			catch (RuntimeException e) {
				System.out.printf("  ! %s: %s%n", e.getClass().getSimpleName(), e.getMessage());
			}
		}
		System.out.println("\nBye.");
		return 0;
	}

	/** Non-interactive: run the scripted suite once and report the number of failures. */
	int runSmokeSuite() {
		return new SmokeTest(context, webRunning, reportDir).run();
	}

	// ---------------------------------------------------------------- menu

	private void banner() {
		System.out.println("""

				================================================================
				 CarRental backend - console harness
				================================================================
				 Calls the real services in-process. No Docker, no frontend.""");
		if (webRunning) {
			System.out.println(" REST API is ALSO live on http://localhost:8080 (same data).");
		}
		else {
			System.out.println(" Headless: no web server, embedded in-memory H2 only.");
		}
		System.out.println("""
				 Seeded logins: admin@nusiss.edu / Admin123!
				                staff@nusiss.edu / Staff123!
				                customer@nusiss.edu / Customer123!
				 Blank input at a required prompt cancels the current action.
				 Note: passwords are echoed - this is a local test harness.
				================================================================""");
	}

	private Map<String, MenuItem> printMenu() {
		List<MenuItem> items = menuItems();
		System.out.printf("%n---- %s ----%n", currentUser == null
				? "not logged in"
				: "%s <%s> [%s]".formatted(currentUser.getFullName(), currentUser.getEmail(), currentUser.getRole()));

		String group = null;
		Map<String, MenuItem> byKey = new LinkedHashMap<>();
		for (MenuItem item : items) {
			if (!item.group().equals(group)) {
				group = item.group();
				System.out.println("  " + group + ":");
			}
			System.out.printf("    %-3s %s%n", item.key(), item.label());
			byKey.put(item.key(), item);
		}
		return byKey;
	}

	private List<MenuItem> menuItems() {
		List<MenuItem> items = new ArrayList<>();

		if (currentUser == null) {
			items.add(new MenuItem("1", "Account", "Register a new customer account", this::doRegister));
			items.add(new MenuItem("2", "Account", "Log in", this::doLogin));
		}
		else {
			items.add(new MenuItem("1", "My account", "View my profile", this::doViewProfile));
			items.add(new MenuItem("2", "My account", "Update my profile", this::doUpdateProfile));
			items.add(new MenuItem("3", "My account", "Show my JWT token", this::doShowToken));
		}

		items.add(new MenuItem("4", "Cars (public)", "Browse available cars", this::doBrowseCars));
		items.add(new MenuItem("5", "Cars (public)", "Search cars (location / type / dates)", this::doSearchCars));
		items.add(new MenuItem("6", "Cars (public)", "View one car by id", this::doViewCar));

		if (is(User.Role.CUSTOMER)) {
			items.add(new MenuItem("10", "Bookings", "Create a booking", this::doCreateBooking));
			items.add(new MenuItem("11", "Bookings", "My booking history", this::doMyBookings));
			items.add(new MenuItem("12", "Bookings", "View one of my bookings", this::doViewMyBooking));
			items.add(new MenuItem("13", "Bookings", "Modify a booking (dates / pickup)", this::doModifyBooking));
			items.add(new MenuItem("14", "Bookings", "Cancel a booking", this::doCancelBooking));
			items.add(new MenuItem("15", "Payments", "Pay for a booking", this::doPay));
			items.add(new MenuItem("16", "Payments", "Payment history for a booking", this::doPaymentHistory));
			items.add(new MenuItem("17", "Payments", "My notifications", this::doMyNotifications));
		}

		if (is(User.Role.STAFF) || is(User.Role.ADMIN)) {
			items.add(new MenuItem("20", "Fleet ops", "Set a car's status", this::doSetCarStatus));
			items.add(new MenuItem("21", "Fleet ops", "Maintenance history for a car", this::doMaintenanceHistory));
			items.add(new MenuItem("22", "Fleet ops", "Update a maintenance record's status", this::doUpdateMaintenance));
			items.add(new MenuItem("23", "Fleet ops", "View any booking by id", this::doViewAnyBooking));
			items.add(new MenuItem("24", "Fleet ops", "Cancel any booking", this::doCancelAnyBooking));
		}

		if (is(User.Role.ADMIN)) {
			items.add(new MenuItem("30", "Admin", "Add a car", this::doCreateCar));
			items.add(new MenuItem("31", "Admin", "Update a car", this::doUpdateCar));
			items.add(new MenuItem("32", "Admin", "Delete a car", this::doDeleteCar));
			items.add(new MenuItem("33", "Admin", "Schedule maintenance", this::doScheduleMaintenance));
			items.add(new MenuItem("34", "Admin", "List all users", this::doListUsers));
			items.add(new MenuItem("35", "Admin", "Enable / disable a user", this::doSetUserEnabled));
			items.add(new MenuItem("36", "Admin", "Change a user's role", this::doSetUserRole));
			items.add(new MenuItem("37", "Admin", "Refund a payment", this::doRefund));
			items.add(new MenuItem("38", "Admin", "Reports summary", this::doReport));
			items.add(new MenuItem("39", "Admin", "Audit log", this::doAuditLog));
		}

		items.add(new MenuItem("88", "Harness", "Run the automated end-to-end smoke test", this::doSmokeTest));
		if (currentUser != null) {
			items.add(new MenuItem("99", "Harness", "Log out", this::doLogout));
		}
		items.add(new MenuItem("0", "Harness", "Exit", () -> running = false));
		return items;
	}

	private record MenuItem(String key, String group, String label, Runnable action) {
	}

	private boolean is(User.Role role) {
		return currentUser != null && currentUser.getRole() == role;
	}

	// ---------------------------------------------------------------- account actions

	private void doRegister() {
		RegisterRequest request = validated(new RegisterRequest(
				required("Full name"),
				required("Email"),
				required("Password (min 8 chars)"),
				optional("Phone (optional)")));
		adopt(authService.register(request), "Registered");
	}

	private void doLogin() {
		LoginRequest request = validated(new LoginRequest(required("Email"), required("Password")));
		adopt(authService.login(request), "Logged in");
	}

	/** Mirrors what a client does with an AuthResponse: keep the token, remember who you are. */
	private void adopt(AuthResponse response, String what) {
		this.currentToken = response.token();
		this.currentUser = userRepository.findById(response.userId())
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User vanished after auth"));
		System.out.printf("  %s as #%d %s [%s]. JWT issued (%d chars, valid=%s).%n",
				what, response.userId(), response.fullName(), response.role(),
				response.token().length(), jwtService.isValid(response.token()));
	}

	private void doLogout() {
		System.out.println("  Logged out " + currentUser.getEmail());
		currentUser = null;
		currentToken = null;
	}

	private void doViewProfile() {
		User me = reload();
		System.out.printf("  id=%d%n  name=%s%n  email=%s%n  phone=%s%n  role=%s%n  enabled=%s%n  createdAt=%s%n",
				me.getId(), me.getFullName(), me.getEmail(), me.getPhone(), me.getRole(), me.isEnabled(),
				TS.format(me.getCreatedAt().atZone(ZoneId.systemDefault())));
	}

	private void doUpdateProfile() {
		User me = reload();
		UpdateProfileRequest request = validated(new UpdateProfileRequest(
				requiredWithDefault("Full name", me.getFullName()),
				optionalWithDefault("Phone", me.getPhone())));
		currentUser = userService.updateProfile(me, request);
		System.out.printf("  Updated: %s / %s%n", currentUser.getFullName(), currentUser.getPhone());
	}

	private void doShowToken() {
		System.out.println("  Bearer " + currentToken);
		System.out.println("  (usable as: Authorization: Bearer <token> against the REST API)");
	}

	// ---------------------------------------------------------------- car actions

	private void doBrowseCars() {
		printCars(carService.browseAvailable());
	}

	private void doSearchCars() {
		String location = optional("Location (blank = any)");
		Car.CarType type = optionalEnum("Type", Car.CarType.class);
		LocalDate start = optionalDate("Start date yyyy-MM-dd (blank = any)");
		LocalDate end = start == null ? null : requiredDate("End date yyyy-MM-dd", start.plusDays(2));

		List<Car> results = (location == null && type == null && start == null)
				? carService.browseAvailable()
				: carService.search(location, type, start, end);
		printCars(results);
	}

	private void doViewCar() {
		Car car = carService.getById(requiredLong("Car id"));
		System.out.printf("  #%d %s %s (%d) - %s, %s, %s/day, status=%s%n",
				car.getId(), car.getMake(), car.getModel(), car.getYear(),
				car.getType(), car.getLocation(), car.getDailyRate(), car.getStatus());
	}

	private void doCreateCar() {
		CarRequest request = validated(new CarRequest(
				required("Make"), required("Model"), (int) requiredLong("Year"),
				requiredAmount("Daily rate"), required("Location"),
				requiredEnum("Type", Car.CarType.class, Car.CarType.SEDAN)));
		Car car = carService.create(request, currentUser.getEmail());
		System.out.printf("  Created car #%d %s %s%n", car.getId(), car.getMake(), car.getModel());
	}

	private void doUpdateCar() {
		Car car = carService.getById(requiredLong("Car id"));
		CarRequest request = validated(new CarRequest(
				requiredWithDefault("Make", car.getMake()),
				requiredWithDefault("Model", car.getModel()),
				(int) requiredLongWithDefault("Year", car.getYear()),
				requiredAmountWithDefault("Daily rate", car.getDailyRate()),
				requiredWithDefault("Location", car.getLocation()),
				requiredEnum("Type", Car.CarType.class, car.getType())));
		Car saved = carService.update(car.getId(), request, currentUser.getEmail());
		System.out.printf("  Updated car #%d -> %s %s, %s/day, %s, %s%n", saved.getId(), saved.getMake(),
				saved.getModel(), saved.getDailyRate(), saved.getLocation(), saved.getType());
	}

	private void doDeleteCar() {
		long id = requiredLong("Car id");
		Car car = carService.getById(id);
		if (!confirm("Delete #%d %s %s?".formatted(id, car.getMake(), car.getModel()))) {
			throw new Cancelled();
		}
		carService.delete(id, currentUser.getEmail());
		System.out.println("  Deleted car #" + id);
	}

	private void doSetCarStatus() {
		long id = requiredLong("Car id");
		Car.CarStatus status = requiredEnum("New status", Car.CarStatus.class, Car.CarStatus.AVAILABLE);
		Car car = carService.setStatus(id, status, currentUser.getEmail());
		System.out.printf("  Car #%d is now %s%n", car.getId(), car.getStatus());
	}

	// ---------------------------------------------------------------- booking actions

	private void doCreateBooking() {
		LocalDate tomorrow = LocalDate.now().plusDays(1);
		BookingRequest request = validated(new BookingRequest(
				requiredLong("Car id"),
				requiredDate("Start date yyyy-MM-dd", tomorrow),
				requiredDate("End date yyyy-MM-dd", tomorrow.plusDays(2)),
				optional("Pickup location (optional)")));
		printBooking(bookingService.create(currentUser, request), "Created");
	}

	private void doMyBookings() {
		printBookings(bookingService.historyFor(currentUser.getId()));
	}

	private void doViewMyBooking() {
		printBooking(bookingService.getOwned(currentUser, requiredLong("Booking id")), "Booking");
	}

	private void doViewAnyBooking() {
		printBooking(bookingService.getById(requiredLong("Booking id")), "Booking");
	}

	private void doModifyBooking() {
		Booking existing = bookingService.getOwned(currentUser, requiredLong("Booking id"));
		BookingUpdateRequest request = validated(new BookingUpdateRequest(
				requiredDate("New start date yyyy-MM-dd", existing.getStartDate()),
				requiredDate("New end date yyyy-MM-dd", existing.getEndDate()),
				optionalWithDefault("Pickup location", existing.getPickupLocation())));
		printBooking(bookingService.modify(currentUser, existing.getId(), request), "Modified");
	}

	private void doCancelBooking() {
		printBooking(bookingService.cancel(currentUser, requiredLong("Booking id")), "Cancelled");
	}

	private void doCancelAnyBooking() {
		printBooking(bookingService.cancel(currentUser, requiredLong("Booking id")), "Cancelled");
	}

	// ---------------------------------------------------------------- payment actions

	private void doPay() {
		PaymentRequest request = validated(new PaymentRequest(
				requiredLong("Booking id"),
				requiredEnum("Method", Payment.Method.class, Payment.Method.CARD),
				optional("Card number (blank = none; one starting 0000 is declined)")));
		Payment payment = paymentService.pay(currentUser, request);
		printPayment(payment, "Paid");
		System.out.printf("  Booking #%d is now %s%n",
				payment.getBooking().getId(), payment.getBooking().getStatus());
	}

	private void doPaymentHistory() {
		List<Payment> payments = paymentService.historyFor(requiredLong("Booking id"));
		if (payments.isEmpty()) {
			System.out.println("  (no payments for that booking)");
			return;
		}
		payments.forEach(p -> printPayment(p, "-"));
	}

	private void doRefund() {
		Payment payment = paymentService.refund(requiredLong("Payment id"), currentUser.getEmail());
		printPayment(payment, "Refunded");
		System.out.printf("  Booking #%d is now %s%n",
				payment.getBooking().getId(), payment.getBooking().getStatus());
	}

	private void doMyNotifications() {
		List<Notification> notifications = notificationRepository
				.findByRecipientIdOrderBySentAtDesc(currentUser.getId());
		if (notifications.isEmpty()) {
			System.out.println("  (no notifications yet)");
			return;
		}
		notifications.forEach(n -> System.out.printf("  [%s] %-18s %s%n",
				TS.format(n.getSentAt().atZone(ZoneId.systemDefault())), n.getType(), n.getMessage()));
	}

	// ---------------------------------------------------------------- maintenance actions

	private void doScheduleMaintenance() {
		MaintenanceRequest request = validated(new MaintenanceRequest(
				requiredLong("Car id"),
				required("Description"),
				requiredDate("Scheduled date yyyy-MM-dd", LocalDate.now())));
		MaintenanceRecord record = maintenanceService.schedule(request, currentUser.getEmail());
		System.out.printf("  Scheduled record #%d on car #%d for %s (car is now %s)%n",
				record.getId(), record.getCar().getId(), record.getScheduledDate(), record.getCar().getStatus());
	}

	private void doUpdateMaintenance() {
		long id = requiredLong("Maintenance record id");
		MaintenanceRecord.Status status = requiredEnum("New status", MaintenanceRecord.Status.class,
				MaintenanceRecord.Status.IN_PROGRESS);
		MaintenanceRecord record = maintenanceService.updateStatus(id, status, currentUser.getEmail());
		System.out.printf("  Record #%d is %s (completed=%s), car #%d is %s%n", record.getId(), record.getStatus(),
				record.getCompletedDate(), record.getCar().getId(), record.getCar().getStatus());
	}

	private void doMaintenanceHistory() {
		List<MaintenanceRecord> records = maintenanceService.historyFor(requiredLong("Car id"));
		if (records.isEmpty()) {
			System.out.println("  (no maintenance records for that car)");
			return;
		}
		records.forEach(r -> System.out.printf("  #%-4d %-12s scheduled=%s completed=%s  %s%n",
				r.getId(), r.getStatus(), r.getScheduledDate(), r.getCompletedDate(), r.getDescription()));
	}

	// ---------------------------------------------------------------- admin actions

	private void doListUsers() {
		System.out.printf("  %-4s %-22s %-26s %-9s %s%n", "id", "name", "email", "role", "enabled");
		for (User u : userService.listAll()) {
			System.out.printf("  %-4d %-22.22s %-26.26s %-9s %s%n",
					u.getId(), u.getFullName(), u.getEmail(), u.getRole(), u.isEnabled());
		}
	}

	private void doSetUserEnabled() {
		long id = requiredLong("User id");
		boolean enabled = confirm("Enabled?");
		User user = userService.setEnabled(id, enabled, currentUser.getEmail());
		System.out.printf("  %s enabled=%s%n", user.getEmail(), user.isEnabled());
	}

	private void doSetUserRole() {
		long id = requiredLong("User id");
		User.Role role = requiredEnum("New role", User.Role.class, User.Role.CUSTOMER);
		User user = userService.setRole(id, role, currentUser.getEmail());
		System.out.printf("  %s is now %s%n", user.getEmail(), user.getRole());
		if (currentUser.getId().equals(user.getId())) {
			currentUser = user;
			System.out.println("  (that was you - the menu changes accordingly)");
		}
	}

	private void doReport() {
		ReportSummary s = reportService.summary();
		System.out.printf("""
				  cars:      %d total, %d available, %d in maintenance
				  bookings:  %d total, %d confirmed, %d cancelled
				  revenue:   %s (successful payments only)
				  by type:   %s%n""",
				s.totalCars(), s.availableCars(), s.carsInMaintenance(),
				s.totalBookings(), s.confirmedBookings(), s.cancelledBookings(),
				s.totalRevenue(), s.bookingsByCarType());
	}

	private void doAuditLog() {
		int limit = (int) requiredLongWithDefault("How many entries", 20);
		List<AuditLog> logs = auditLogRepository
				.findAllByOrderByTimestampDesc(PageRequest.of(0, Math.min(Math.max(limit, 1), 500)));
		for (AuditLog log : logs) {
			System.out.printf("  [%s] %-24s %-14s %-18s #%s %s%n",
					TS.format(log.getTimestamp().atZone(ZoneId.systemDefault())),
					String.valueOf(log.getActorEmail()), log.getEntityType(), log.getAction(),
					log.getEntityId(), log.getDetails() == null ? "" : log.getDetails());
		}
	}

	private void doSmokeTest() {
		int failures = new SmokeTest(context, webRunning, reportDir).run();
		if (failures == 0) {
			System.out.println("  Smoke test: everything passed.");
		}
	}

	// ---------------------------------------------------------------- printing

	private void printCars(List<Car> cars) {
		if (cars.isEmpty()) {
			System.out.println("  (no cars match)");
			return;
		}
		System.out.printf("  %-4s %-10s %-12s %-6s %-10s %-12s %10s %s%n",
				"id", "make", "model", "year", "type", "location", "rate/day", "status");
		for (Car c : cars) {
			System.out.printf("  %-4d %-10.10s %-12.12s %-6d %-10s %-12.12s %10s %s%n",
					c.getId(), c.getMake(), c.getModel(), c.getYear(), c.getType(),
					c.getLocation(), c.getDailyRate(), c.getStatus());
		}
		System.out.println("  " + cars.size() + " car(s)");
	}

	private void printBookings(List<Booking> bookings) {
		if (bookings.isEmpty()) {
			System.out.println("  (no bookings)");
			return;
		}
		System.out.printf("  %-4s %-18s %-12s %-12s %10s %s%n",
				"id", "car", "from", "to", "amount", "status");
		for (Booking b : bookings) {
			System.out.printf("  %-4d %-18.18s %-12s %-12s %10s %s%n",
					b.getId(), b.getCar().getMake() + " " + b.getCar().getModel(),
					b.getStartDate(), b.getEndDate(), b.getTotalAmount(), b.getStatus());
		}
		System.out.println("  " + bookings.size() + " booking(s)");
	}

	private void printBooking(Booking b, String what) {
		System.out.printf("  %s booking #%d: car #%d %s %s, %s -> %s, pickup=%s, total=%s, status=%s%n",
				what, b.getId(), b.getCar().getId(), b.getCar().getMake(), b.getCar().getModel(),
				b.getStartDate(), b.getEndDate(), b.getPickupLocation(), b.getTotalAmount(), b.getStatus());
	}

	private void printPayment(Payment p, String what) {
		System.out.printf("  %s payment #%d: booking #%d, %s via %s, %s, ref=%s%n",
				what, p.getId(), p.getBooking().getId(), p.getAmount(), p.getMethod(),
				p.getStatus(), p.getTransactionRef());
	}

	// ---------------------------------------------------------------- input helpers

	/** Runs the same bean validation the controllers get from {@code @Valid}. */
	private <T> T validated(T request) {
		if (validator == null) {
			return request;
		}
		Set<ConstraintViolation<T>> violations = validator.validate(request);
		if (!violations.isEmpty()) {
			String message = violations.stream()
					.map(v -> v.getPropertyPath() + ": " + v.getMessage())
					.sorted()
					.collect(Collectors.joining("; "));
			throw new ApiException(HttpStatus.BAD_REQUEST, message);
		}
		return request;
	}

	private User reload() {
		return userRepository.findById(currentUser.getId())
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Your account no longer exists"));
	}

	private String readLine(String label) {
		System.out.print("  " + label + ": ");
		System.out.flush();
		if (!in.hasNextLine()) {
			throw new EndOfInput();
		}
		return in.nextLine();
	}

	private String required(String label) {
		String value = readLine(label).trim();
		if (value.isEmpty()) {
			throw new Cancelled();
		}
		return value;
	}

	private String optional(String label) {
		String value = readLine(label).trim();
		return value.isEmpty() ? null : value;
	}

	private String requiredWithDefault(String label, String defaultValue) {
		String value = readLine("%s [%s]".formatted(label, defaultValue)).trim();
		return value.isEmpty() ? defaultValue : value;
	}

	private String optionalWithDefault(String label, String defaultValue) {
		String value = readLine("%s [%s]".formatted(label, defaultValue)).trim();
		if (value.isEmpty()) {
			return defaultValue;
		}
		return "-".equals(value) ? null : value;
	}

	private long requiredLong(String label) {
		return parseLong(required(label));
	}

	private long requiredLongWithDefault(String label, long defaultValue) {
		return parseLong(requiredWithDefault(label, String.valueOf(defaultValue)));
	}

	private long parseLong(String raw) {
		try {
			return Long.parseLong(raw);
		}
		catch (NumberFormatException e) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "'" + raw + "' is not a whole number");
		}
	}

	private BigDecimal requiredAmount(String label) {
		return parseAmount(required(label));
	}

	private BigDecimal requiredAmountWithDefault(String label, BigDecimal defaultValue) {
		return parseAmount(requiredWithDefault(label, defaultValue.toPlainString()));
	}

	private BigDecimal parseAmount(String raw) {
		try {
			return new BigDecimal(raw);
		}
		catch (NumberFormatException e) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "'" + raw + "' is not an amount");
		}
	}

	private LocalDate requiredDate(String label, LocalDate defaultValue) {
		return parseDate(requiredWithDefault(label, defaultValue.toString()));
	}

	private LocalDate optionalDate(String label) {
		String value = optional(label);
		return value == null ? null : parseDate(value);
	}

	private LocalDate parseDate(String raw) {
		try {
			return LocalDate.parse(raw);
		}
		catch (DateTimeParseException e) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "'" + raw + "' is not a yyyy-MM-dd date");
		}
	}

	private <E extends Enum<E>> E requiredEnum(String label, Class<E> type, E defaultValue) {
		String raw = requiredWithDefault("%s %s".formatted(label, options(type)), defaultValue.name());
		return parseEnum(type, raw);
	}

	private <E extends Enum<E>> E optionalEnum(String label, Class<E> type) {
		String raw = optional("%s %s (blank = any)".formatted(label, options(type)));
		return raw == null ? null : parseEnum(type, raw);
	}

	private <E extends Enum<E>> E parseEnum(Class<E> type, String raw) {
		try {
			return Enum.valueOf(type, raw.trim().toUpperCase());
		}
		catch (IllegalArgumentException e) {
			throw new ApiException(HttpStatus.BAD_REQUEST,
					"'" + raw + "' is not one of " + options(type));
		}
	}

	private String options(Class<? extends Enum<?>> type) {
		return "(" + java.util.Arrays.stream(type.getEnumConstants())
				.map(Enum::name).collect(Collectors.joining("|")) + ")";
	}

	private boolean confirm(String label) {
		String value = readLine(label + " y/n").trim().toLowerCase();
		if (value.isEmpty()) {
			throw new Cancelled();
		}
		return value.startsWith("y");
	}

	/** stdin ran out (piped input, or Ctrl-D) - unwind and shut down cleanly. */
	private static final class EndOfInput extends RuntimeException {
	}

	/** Blank answer at a required prompt - abandon this action, keep the menu running. */
	private static final class Cancelled extends RuntimeException {
	}
}

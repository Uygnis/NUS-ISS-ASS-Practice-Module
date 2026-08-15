package org.rentez.reservationservice;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rentez.reservationservice.client.CatalogClient;
import org.rentez.reservationservice.client.InternalCarView;
import org.rentez.reservationservice.error.ApiException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Behavioural parity with the monolith's {@code BookingService}.
 *
 * <p>Every rule below was read off the original implementation and is asserted
 * here so the port can be shown not to have changed behaviour: the same status
 * codes, the same messages, the same pricing, the same ownership checks. The two
 * places where behaviour deliberately differs are called out in their own tests.
 *
 * <p>Catalog is stubbed. It is a separate service with its own tests; what
 * matters here is that reservation asks it the right question and reacts
 * correctly to each answer.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class BookingRulesIntegrationTest {

	private static final long CAR_ID = 42L;
	private static final long CUSTOMER_ID = 3L;

	@Autowired
	private WebApplicationContext context;

	@MockitoBean
	private CatalogClient catalogClient;

	private MockMvc mvc;

	@BeforeEach
	void setUp() {
		mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
		givenCar(CAR_ID, true, "80.00");
	}

	private void givenCar(long carId, boolean rentable, String dailyRate) {
		given(catalogClient.getCar(carId)).willReturn(new InternalCarView(
				carId, "Toyota", "Corolla", 2023, "SEDAN", "Jurong", new BigDecimal(dailyRate), rentable));
	}

	private String book(String token, long carId, String start, String end) throws Exception {
		return mvc.perform(post("/api/reservations/bookings")
						.header("Authorization", "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"carId":%d,"startDate":"%s","endDate":"%s","pickupLocation":"Jurong"}
								""".formatted(carId, start, end)))
				.andReturn().getResponse().getContentAsString();
	}

	private static String id(String json) {
		Number value = JsonPath.read(json, "$.id");
		return value.toString();
	}

	// ------------------------------------------------------------------ create

	/** Inclusive of both ends, never fewer than one day: 1-3 Sep at 80.00 is 240.00. */
	@Test
	void pricesAnInclusiveDateRange() throws Exception {
		mvc.perform(post("/api/reservations/bookings")
						.header("Authorization", "Bearer " + TestTokens.customer(CUSTOMER_ID))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"carId":42,"startDate":"2026-09-01","endDate":"2026-09-03","pickupLocation":"Jurong"}
								"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.totalAmount").value(240.00))
				.andExpect(jsonPath("$.status").value("PENDING_PAYMENT"))
				// Snapshotted from catalog, so the booking can render itself later.
				.andExpect(jsonPath("$.carMake").value("Toyota"))
				.andExpect(jsonPath("$.carType").value("SEDAN"))
				.andExpect(jsonPath("$.dailyRate").value(80.00));
	}

	@Test
	void aSingleDayBookingCostsOneDay() throws Exception {
		mvc.perform(post("/api/reservations/bookings")
						.header("Authorization", "Bearer " + TestTokens.customer(CUSTOMER_ID))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"carId":42,"startDate":"2026-09-05","endDate":"2026-09-05"}
								"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.totalAmount").value(80.00));
	}

	@Test
	void rejectsAnInvertedDateRange() throws Exception {
		mvc.perform(post("/api/reservations/bookings")
						.header("Authorization", "Bearer " + TestTokens.customer(CUSTOMER_ID))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"carId":42,"startDate":"2026-09-10","endDate":"2026-09-08"}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("endDate cannot be before startDate"));
	}

	@Test
	void rejectsACarThatIsNotRentable() throws Exception {
		givenCar(99L, false, "80.00");

		mvc.perform(post("/api/reservations/bookings")
						.header("Authorization", "Bearer " + TestTokens.customer(CUSTOMER_ID))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"carId":99,"startDate":"2026-09-01","endDate":"2026-09-02"}
								"""))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value("This car is not currently rentable"));
	}

	@Test
	void propagatesAnUnknownCarAsNotFound() throws Exception {
		given(catalogClient.getCar(1234L))
				.willThrow(new ApiException(HttpStatus.NOT_FOUND, "No car with id 1234"));

		mvc.perform(post("/api/reservations/bookings")
						.header("Authorization", "Bearer " + TestTokens.customer(CUSTOMER_ID))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"carId":1234,"startDate":"2026-09-01","endDate":"2026-09-02"}
								"""))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.message").value("No car with id 1234"));
	}

	/** A catalog outage must not masquerade as a business error. */
	@Test
	void reportsACatalogOutageAsServiceUnavailable() throws Exception {
		given(catalogClient.getCar(77L))
				.willThrow(new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Catalog service is unavailable"));

		mvc.perform(post("/api/reservations/bookings")
						.header("Authorization", "Bearer " + TestTokens.customer(CUSTOMER_ID))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"carId":77,"startDate":"2026-09-01","endDate":"2026-09-02"}
								"""))
				.andExpect(status().isServiceUnavailable());
	}

	// ------------------------------------------------------- overlap / booking_day

	@Test
	void refusesAnOverlappingBookingForTheSameCar() throws Exception {
		String token = TestTokens.customer(CUSTOMER_ID);
		book(token, CAR_ID, "2026-10-01", "2026-10-05");

		mvc.perform(post("/api/reservations/bookings")
						.header("Authorization", "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"carId":42,"startDate":"2026-10-04","endDate":"2026-10-08"}
								"""))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message")
						.value("This car is already booked for part of that date range"));
	}

	/** Adjacent, not overlapping: the day after an end date is free. */
	@Test
	void allowsABookingThatStartsTheDayAfterAnotherEnds() throws Exception {
		String token = TestTokens.customer(CUSTOMER_ID);
		book(token, CAR_ID, "2026-11-01", "2026-11-03");

		mvc.perform(post("/api/reservations/bookings")
						.header("Authorization", "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"carId":42,"startDate":"2026-11-04","endDate":"2026-11-06"}
								"""))
				.andExpect(status().isCreated());
	}

	/** Cancelling must put the days back on the market. */
	@Test
	void cancellingReleasesTheDatesForRebooking() throws Exception {
		String token = TestTokens.customer(CUSTOMER_ID);
		String booked = book(token, CAR_ID, "2026-12-01", "2026-12-03");

		mvc.perform(delete("/api/reservations/bookings/" + id(booked))
						.header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("CANCELLED"));

		mvc.perform(post("/api/reservations/bookings")
						.header("Authorization", "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"carId":42,"startDate":"2026-12-01","endDate":"2026-12-03"}
								"""))
				.andExpect(status().isCreated());
	}

	// ------------------------------------------------------------------ modify

	@Test
	void modifyRepricesAndFlagsAConfirmedBookingAsModified() throws Exception {
		String token = TestTokens.customer(CUSTOMER_ID);
		String booked = book(token, CAR_ID, "2027-01-01", "2027-01-02");
		String bookingId = id(booked);

		// Confirm it first, so the CONFIRMED -> MODIFIED transition is exercised.
		mvc.perform(post("/api/reservations/internal/bookings/" + bookingId + "/confirm")
						.header("Authorization", "Bearer " + TestTokens.service()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("CONFIRMED"));

		mvc.perform(put("/api/reservations/bookings/" + bookingId)
						.header("Authorization", "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"startDate":"2027-01-01","endDate":"2027-01-04","pickupLocation":"Changi"}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("MODIFIED"))
				.andExpect(jsonPath("$.totalAmount").value(320.00))
				.andExpect(jsonPath("$.pickupLocation").value("Changi"));
	}

	@Test
	void modifyRefusesACancelledBooking() throws Exception {
		String token = TestTokens.customer(CUSTOMER_ID);
		String booked = book(token, CAR_ID, "2027-02-01", "2027-02-02");
		String bookingId = id(booked);

		mvc.perform(delete("/api/reservations/bookings/" + bookingId)
				.header("Authorization", "Bearer " + token)).andExpect(status().isOk());

		mvc.perform(put("/api/reservations/bookings/" + bookingId)
						.header("Authorization", "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"startDate":"2027-02-01","endDate":"2027-02-05"}
								"""))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value("Cannot modify a CANCELLED booking"));
	}

	/** Re-dating onto another booking's dates must be refused. */
	@Test
	void modifyRefusesDatesHeldByAnotherBooking() throws Exception {
		String token = TestTokens.customer(CUSTOMER_ID);
		book(token, CAR_ID, "2027-03-10", "2027-03-12");
		String second = book(token, CAR_ID, "2027-03-20", "2027-03-22");

		mvc.perform(put("/api/reservations/bookings/" + id(second))
						.header("Authorization", "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"startDate":"2027-03-11","endDate":"2027-03-13"}
								"""))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message")
						.value("This car is already booked for part of that date range"));
	}

	/** Re-dating within a booking's own held range must NOT collide with itself. */
	@Test
	void modifyCanShrinkOrShiftWithinItsOwnDates() throws Exception {
		String token = TestTokens.customer(CUSTOMER_ID);
		String booked = book(token, CAR_ID, "2027-04-01", "2027-04-10");

		mvc.perform(put("/api/reservations/bookings/" + id(booked))
						.header("Authorization", "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"startDate":"2027-04-03","endDate":"2027-04-05"}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalAmount").value(240.00));
	}

	// --------------------------------------------------------------- ownership

	@Test
	void aCustomerCannotReadOrTouchAnotherCustomersBooking() throws Exception {
		String owner = TestTokens.customer(CUSTOMER_ID);
		String intruder = TestTokens.customer(999L);
		String bookingId = id(book(owner, CAR_ID, "2027-05-01", "2027-05-02"));

		mvc.perform(get("/api/reservations/bookings/" + bookingId)
						.header("Authorization", "Bearer " + intruder))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.message").value("This booking does not belong to you"));

		mvc.perform(delete("/api/reservations/bookings/" + bookingId)
						.header("Authorization", "Bearer " + intruder))
				.andExpect(status().isForbidden());

		mvc.perform(put("/api/reservations/bookings/" + bookingId)
						.header("Authorization", "Bearer " + intruder)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"startDate":"2027-05-01","endDate":"2027-05-09"}
								"""))
				.andExpect(status().isForbidden());
	}

	@Test
	void myBookingsReturnsOnlyMineNewestFirst() throws Exception {
		String mine = TestTokens.customer(4242L);
		book(mine, CAR_ID, "2027-06-01", "2027-06-02");
		book(mine, CAR_ID, "2027-06-10", "2027-06-11");
		book(TestTokens.customer(CUSTOMER_ID), CAR_ID, "2027-06-20", "2027-06-21");

		mvc.perform(get("/api/reservations/bookings/me").header("Authorization", "Bearer " + mine))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[0].startDate").value("2027-06-10"))
				.andExpect(jsonPath("$[1].startDate").value("2027-06-01"));
	}

	@Test
	void unknownBookingIsANotFound() throws Exception {
		mvc.perform(get("/api/reservations/bookings/987654")
						.header("Authorization", "Bearer " + TestTokens.customer(CUSTOMER_ID)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.message").value("No booking with id 987654"));
	}

	// ------------------------------------------------------------------ cancel

	/** Cancelling twice is a no-op, exactly as in the monolith. */
	@Test
	void cancellingAnAlreadyCancelledBookingIsIdempotent() throws Exception {
		String token = TestTokens.customer(CUSTOMER_ID);
		String bookingId = id(book(token, CAR_ID, "2027-07-01", "2027-07-02"));

		mvc.perform(delete("/api/reservations/bookings/" + bookingId)
				.header("Authorization", "Bearer " + token)).andExpect(status().isOk());

		mvc.perform(delete("/api/reservations/bookings/" + bookingId)
						.header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("CANCELLED"));
	}

	// ----------------------------------------------------------------- internal

	/** Payment retries confirm whenever a response is lost, so it must stay a success. */
	@Test
	void confirmIsIdempotentAndServiceOnly() throws Exception {
		String token = TestTokens.customer(CUSTOMER_ID);
		String bookingId = id(book(token, CAR_ID, "2027-08-01", "2027-08-02"));

		mvc.perform(post("/api/reservations/internal/bookings/" + bookingId + "/confirm")
						.header("Authorization", "Bearer " + TestTokens.service()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("CONFIRMED"));

		mvc.perform(post("/api/reservations/internal/bookings/" + bookingId + "/confirm")
						.header("Authorization", "Bearer " + TestTokens.service()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("CONFIRMED"));

		// A customer token must not reach the saga endpoints.
		mvc.perform(post("/api/reservations/internal/bookings/" + bookingId + "/confirm")
						.header("Authorization", "Bearer " + token))
				.andExpect(status().isForbidden());
	}

	@Test
	void confirmRefusesACancelledBooking() throws Exception {
		String token = TestTokens.customer(CUSTOMER_ID);
		String bookingId = id(book(token, CAR_ID, "2027-09-01", "2027-09-02"));

		mvc.perform(delete("/api/reservations/bookings/" + bookingId)
				.header("Authorization", "Bearer " + token)).andExpect(status().isOk());

		mvc.perform(post("/api/reservations/internal/bookings/" + bookingId + "/confirm")
						.header("Authorization", "Bearer " + TestTokens.service()))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value("Cannot confirm a CANCELLED booking"));
	}

	/**
	 * The monolith's non-customer cancel branch, which its CUSTOMER-only
	 * controller made unreachable. It lives on the internal API now and skips the
	 * ownership check, because the caller is payment-service compensating a refund.
	 */
	@Test
	void internalCancelSkipsTheOwnershipCheck() throws Exception {
		String bookingId = id(book(TestTokens.customer(CUSTOMER_ID), CAR_ID, "2027-10-01", "2027-10-02"));

		mvc.perform(post("/api/reservations/internal/bookings/" + bookingId + "/cancel")
						.header("Authorization", "Bearer " + TestTokens.service()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("CANCELLED"));
	}

	// ------------------------------------------------------------- availability

	@Test
	void availabilityIsPublicAndExcludesBookedCars() throws Exception {
		given(catalogClient.findRentable(any(), any())).willReturn(List.of(
				new InternalCarView(CAR_ID, "Toyota", "Corolla", 2023, "SEDAN", "Jurong",
						new BigDecimal("80.00"), true),
				new InternalCarView(43L, "Honda", "Civic", 2023, "SEDAN", "Jurong",
						new BigDecimal("72.50"), true)));

		book(TestTokens.customer(CUSTOMER_ID), CAR_ID, "2028-01-01", "2028-01-05");

		// No Authorization header at all - window-shopping worked without an
		// account in the monolith and still does.
		mvc.perform(get("/api/reservations/availability")
						.param("startDate", "2028-01-03").param("endDate", "2028-01-04"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].carId").value(43));
	}

	@Test
	void availabilityWithoutDatesReturnsEveryRentableCar() throws Exception {
		given(catalogClient.findRentable(any(), any())).willReturn(List.of(
				new InternalCarView(CAR_ID, "Toyota", "Corolla", 2023, "SEDAN", "Jurong",
						new BigDecimal("80.00"), true)));

		mvc.perform(get("/api/reservations/availability"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1));
	}

	// ------------------------------------------------------------------- stats

	/**
	 * ReportService grouped bookings by {@code b.getCar().getType()}, dereferencing
	 * a Car per booking. The snapshotted type makes it a local GROUP BY.
	 */
	@Test
	void statsGroupByTheSnapshottedCarTypeWithoutCallingCatalog() throws Exception {
		// A type no other test books, so the assertion holds regardless of what
		// else has run against this shared container.
		long luxuryCarId = 7001L;
		given(catalogClient.getCar(luxuryCarId)).willReturn(new InternalCarView(
				luxuryCarId, "Tesla", "Model S", 2024, "LUXURY", "Changi",
				new BigDecimal("200.00"), true));

		String token = TestTokens.customer(CUSTOMER_ID);
		book(token, luxuryCarId, "2028-03-01", "2028-03-02");
		book(token, luxuryCarId, "2028-03-10", "2028-03-11");

		mvc.perform(get("/api/reservations/internal/stats")
						.header("Authorization", "Bearer " + TestTokens.service()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.bookingsByCarType.LUXURY").value(2));
	}
}

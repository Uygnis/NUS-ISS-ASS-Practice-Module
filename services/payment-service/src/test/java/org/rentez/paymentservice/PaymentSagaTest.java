package org.rentez.paymentservice;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rentez.paymentservice.client.BookingView;
import org.rentez.paymentservice.client.ReservationClient;
import org.rentez.paymentservice.client.NotificationClient;
import org.rentez.paymentservice.domain.ConfirmState;
import org.rentez.paymentservice.domain.Payment;
import org.rentez.paymentservice.domain.PaymentStatus;
import org.rentez.paymentservice.error.ApiException;
import org.rentez.paymentservice.repository.PaymentRepository;
import org.rentez.paymentservice.service.ReconciliationSweeper;
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
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The payment saga and its failure modes.
 *
 * <p>The monolith did this in one method against one database, so most of these
 * situations could not arise. Splitting payment from booking makes each of them
 * reachable, and the tests below are the argument that each is handled rather
 * than hoped away. The business rules themselves - ownership, awaiting-payment,
 * the mock gateway's "0000" rule, the messages - are asserted unchanged.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class PaymentSagaTest {

	private static final long CUSTOMER_ID = 3L;
	private static final AtomicLong BOOKINGS = new AtomicLong(9_000L);

	@Autowired
	private WebApplicationContext context;

	@Autowired
	private PaymentRepository paymentRepository;

	@Autowired
	private ReconciliationSweeper sweeper;

	@MockitoBean
	private ReservationClient reservationClient;

	@MockitoBean
	private NotificationClient notificationClient;

	private MockMvc mvc;

	@BeforeEach
	void setUp() {
		mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
		willDoNothing().given(notificationClient).send(anyString(), anyString());
		willDoNothing().given(reservationClient).confirmBooking(anyLong());
		willDoNothing().given(reservationClient).cancelBooking(anyLong());
	}

	/** A fresh booking id per test, so rows from earlier tests cannot interfere. */
	private long givenBookingAwaitingPayment(String amount) {
		long bookingId = BOOKINGS.incrementAndGet();
		given(reservationClient.getBooking(bookingId)).willReturn(
				new BookingView(bookingId, CUSTOMER_ID, 42L, new BigDecimal(amount), "PENDING_PAYMENT"));
		return bookingId;
	}

	private String payBody(long bookingId, String cardNumber) {
		return cardNumber == null
				? """
				{"bookingId":%d,"method":"WALLET"}
				""".formatted(bookingId)
				: """
				{"bookingId":%d,"method":"CARD","cardNumber":"%s"}
				""".formatted(bookingId, cardNumber);
	}

	// -------------------------------------------------------------- happy path

	@Test
	void chargesTheBookingsOwnAmountAndConfirmsIt() throws Exception {
		long bookingId = givenBookingAwaitingPayment("240.00");

		mvc.perform(post("/api/payments")
						.header("Authorization", "Bearer " + TestTokens.customer(CUSTOMER_ID))
						.contentType(MediaType.APPLICATION_JSON).content(payBody(bookingId, "4111111111111111")))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("SUCCESS"))
				.andExpect(jsonPath("$.confirmState").value("CONFIRMED"))
				// The amount comes from reservation, never from the request body.
				.andExpect(jsonPath("$.amount").value(240.00))
				.andExpect(jsonPath("$.transactionRef").value(org.hamcrest.Matchers.startsWith("TXN-")));

		verify(reservationClient).confirmBooking(bookingId);
	}

	// ------------------------------------------------------------- declinations

	/**
	 * The declined attempt and its audit row must survive the exception. The
	 * monolith persisted both before throwing, and wrapping that in a transaction
	 * would roll them back and erase every record of declined payments.
	 */
	@Test
	void aDeclinedCardIsPersistedBeforeTheErrorIsReturned() throws Exception {
		long bookingId = givenBookingAwaitingPayment("100.00");

		mvc.perform(post("/api/payments")
						.header("Authorization", "Bearer " + TestTokens.customer(CUSTOMER_ID))
						.contentType(MediaType.APPLICATION_JSON).content(payBody(bookingId, "0000123412341234")))
				.andExpect(status().isPaymentRequired())
				.andExpect(jsonPath("$.message").value("Payment was declined by the gateway"));

		List<Payment> attempts = paymentRepository.findByBookingIdOrderByCreatedAtDesc(bookingId);
		assertThat(attempts).hasSize(1);
		assertThat(attempts.get(0).getStatus()).isEqualTo(PaymentStatus.FAILED);
		// A declined payment has no saga to finish, so the sweeper must ignore it.
		assertThat(attempts.get(0).getConfirmState()).isEqualTo(ConfirmState.NOT_APPLICABLE);

		verify(reservationClient, never()).confirmBooking(bookingId);
	}

	/** A declined attempt must not block a later successful one for the same booking. */
	@Test
	void retryingAfterADeclineIsAllowed() throws Exception {
		long bookingId = givenBookingAwaitingPayment("100.00");

		mvc.perform(post("/api/payments")
						.header("Authorization", "Bearer " + TestTokens.customer(CUSTOMER_ID))
						.contentType(MediaType.APPLICATION_JSON).content(payBody(bookingId, "0000999999999999")))
				.andExpect(status().isPaymentRequired());

		mvc.perform(post("/api/payments")
						.header("Authorization", "Bearer " + TestTokens.customer(CUSTOMER_ID))
						.contentType(MediaType.APPLICATION_JSON).content(payBody(bookingId, "4111111111111111")))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("SUCCESS"));
	}

	// ------------------------------------------------------------- preconditions

	@Test
	void refusesToPayForSomeoneElsesBooking() throws Exception {
		long bookingId = givenBookingAwaitingPayment("100.00");

		mvc.perform(post("/api/payments")
						.header("Authorization", "Bearer " + TestTokens.customer(4242L))
						.contentType(MediaType.APPLICATION_JSON).content(payBody(bookingId, null)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.message").value("This booking does not belong to you"));

		assertThat(paymentRepository.findByBookingIdOrderByCreatedAtDesc(bookingId)).isEmpty();
	}

	@Test
	void refusesABookingThatIsNotAwaitingPayment() throws Exception {
		long bookingId = BOOKINGS.incrementAndGet();
		given(reservationClient.getBooking(bookingId)).willReturn(
				new BookingView(bookingId, CUSTOMER_ID, 42L, new BigDecimal("100.00"), "CONFIRMED"));

		mvc.perform(post("/api/payments")
						.header("Authorization", "Bearer " + TestTokens.customer(CUSTOMER_ID))
						.contentType(MediaType.APPLICATION_JSON).content(payBody(bookingId, null)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message")
						.value("This booking is not awaiting payment (status: CONFIRMED)"));
	}

	/** Reservation unreachable before any charge is the safe failure: nothing to undo. */
	@Test
	void failsFastAndChargesNothingWhenReservationIsDown() throws Exception {
		long bookingId = BOOKINGS.incrementAndGet();
		given(reservationClient.getBooking(bookingId))
				.willThrow(new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Reservation service is unavailable"));

		mvc.perform(post("/api/payments")
						.header("Authorization", "Bearer " + TestTokens.customer(CUSTOMER_ID))
						.contentType(MediaType.APPLICATION_JSON).content(payBody(bookingId, null)))
				.andExpect(status().isServiceUnavailable());

		assertThat(paymentRepository.findByBookingIdOrderByCreatedAtDesc(bookingId)).isEmpty();
	}

	// -------------------------------------------------------------- idempotency

	@Test
	void replayingTheSameIdempotencyKeyReturnsTheOriginalPaymentAndChargesOnce() throws Exception {
		long bookingId = givenBookingAwaitingPayment("150.00");
		String key = "checkout-" + bookingId;

		String first = mvc.perform(post("/api/payments")
						.header("Authorization", "Bearer " + TestTokens.customer(CUSTOMER_ID))
						.header("Idempotency-Key", key)
						.contentType(MediaType.APPLICATION_JSON).content(payBody(bookingId, "4111111111111111")))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();

		String second = mvc.perform(post("/api/payments")
						.header("Authorization", "Bearer " + TestTokens.customer(CUSTOMER_ID))
						.header("Idempotency-Key", key)
						.contentType(MediaType.APPLICATION_JSON).content(payBody(bookingId, "4111111111111111")))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();

		assertThat(JsonPath.read(second, "$.id").toString())
				.isEqualTo(JsonPath.read(first, "$.id").toString());
		assertThat(paymentRepository.findByBookingIdOrderByCreatedAtDesc(bookingId)).hasSize(1);
	}

	/**
	 * The safety net for a client that sends no Idempotency-Key: the unique index
	 * on the generated succeeded_booking_id column refuses a second successful
	 * payment for one booking, whatever the application layer believes.
	 */
	@Test
	void aSecondSuccessfulPaymentForOneBookingIsRefusedByTheDatabase() throws Exception {
		long bookingId = givenBookingAwaitingPayment("100.00");

		mvc.perform(post("/api/payments")
						.header("Authorization", "Bearer " + TestTokens.customer(CUSTOMER_ID))
						.contentType(MediaType.APPLICATION_JSON).content(payBody(bookingId, "4111111111111111")))
				.andExpect(status().isCreated());

		// Reservation still reports PENDING_PAYMENT, as it would in the window
		// before confirmation propagates - so the check above cannot catch this.
		mvc.perform(post("/api/payments")
						.header("Authorization", "Bearer " + TestTokens.customer(CUSTOMER_ID))
						.contentType(MediaType.APPLICATION_JSON).content(payBody(bookingId, "4111111111111111")))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value("This booking has already been paid"));

		long succeeded = paymentRepository.findByBookingIdOrderByCreatedAtDesc(bookingId).stream()
				.filter(p -> p.getStatus() == PaymentStatus.SUCCESS)
				.count();
		assertThat(succeeded).isEqualTo(1);
	}

	// ------------------------------------------------------------ saga failures

	/**
	 * The booking was cancelled between the check and the confirm. Retrying can
	 * never succeed, so the money goes back immediately.
	 */
	@Test
	void compensatesWhenTheBookingCanNoLongerBeConfirmed() throws Exception {
		long bookingId = givenBookingAwaitingPayment("200.00");
		willThrow(new ReservationClient.BookingUnconfirmableException("gone"))
				.given(reservationClient).confirmBooking(bookingId);

		mvc.perform(post("/api/payments")
						.header("Authorization", "Bearer " + TestTokens.customer(CUSTOMER_ID))
						.contentType(MediaType.APPLICATION_JSON).content(payBody(bookingId, "4111111111111111")))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("REFUNDED"))
				.andExpect(jsonPath("$.confirmState").value("COMPENSATED"));

		verify(reservationClient).cancelBooking(bookingId);
	}

	/**
	 * Reservation unreachable AFTER the charge. The customer is not told their
	 * payment failed - it did not - and the row is left for reconciliation.
	 */
	@Test
	void leavesThePaymentPendingWhenConfirmationCannotBeDelivered() throws Exception {
		long bookingId = givenBookingAwaitingPayment("120.00");
		willThrow(new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Reservation service is unavailable"))
				.given(reservationClient).confirmBooking(bookingId);

		mvc.perform(post("/api/payments")
						.header("Authorization", "Bearer " + TestTokens.customer(CUSTOMER_ID))
						.contentType(MediaType.APPLICATION_JSON).content(payBody(bookingId, "4111111111111111")))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("SUCCESS"))
				.andExpect(jsonPath("$.confirmState").value("PENDING"));
	}

	// ----------------------------------------------------------- reconciliation

	@Test
	void theSweeperFinishesAPaymentStrandedBeforeConfirmation() throws Exception {
		long bookingId = givenBookingAwaitingPayment("120.00");
		willThrow(new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "down"))
				.given(reservationClient).confirmBooking(bookingId);

		String body = mvc.perform(post("/api/payments")
						.header("Authorization", "Bearer " + TestTokens.customer(CUSTOMER_ID))
						.contentType(MediaType.APPLICATION_JSON).content(payBody(bookingId, "4111111111111111")))
				.andExpect(jsonPath("$.confirmState").value("PENDING"))
				.andReturn().getResponse().getContentAsString();
		long paymentId = ((Number) JsonPath.read(body, "$.id")).longValue();

		// Reservation comes back.
		willDoNothing().given(reservationClient).confirmBooking(bookingId);

		assertThat(sweeper.sweep()).isPositive();
		assertThat(paymentRepository.findById(paymentId).orElseThrow().getConfirmState())
				.isEqualTo(ConfirmState.CONFIRMED);
	}

	/** A payment whose refund also failed is retried until the booking is released. */
	@Test
	void theSweeperRetriesAFailedCompensation() throws Exception {
		long bookingId = givenBookingAwaitingPayment("180.00");
		willThrow(new ReservationClient.BookingUnconfirmableException("gone"))
				.given(reservationClient).confirmBooking(bookingId);
		willThrow(new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "down"))
				.given(reservationClient).cancelBooking(bookingId);

		String body = mvc.perform(post("/api/payments")
						.header("Authorization", "Bearer " + TestTokens.customer(CUSTOMER_ID))
						.contentType(MediaType.APPLICATION_JSON).content(payBody(bookingId, "4111111111111111")))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.confirmState").value("AWAITING_COMPENSATION"))
				.andReturn().getResponse().getContentAsString();
		long paymentId = ((Number) JsonPath.read(body, "$.id")).longValue();

		willDoNothing().given(reservationClient).cancelBooking(bookingId);

		assertThat(sweeper.sweep()).isPositive();
		Payment settled = paymentRepository.findById(paymentId).orElseThrow();
		assertThat(settled.getConfirmState()).isEqualTo(ConfirmState.COMPENSATED);
		assertThat(settled.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
	}

	// ------------------------------------------------------------------- refund

	@Test
	void adminRefundReleasesTheBooking() throws Exception {
		long bookingId = givenBookingAwaitingPayment("300.00");

		String paid = mvc.perform(post("/api/payments")
						.header("Authorization", "Bearer " + TestTokens.customer(CUSTOMER_ID))
						.contentType(MediaType.APPLICATION_JSON).content(payBody(bookingId, "4111111111111111")))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		long paymentId = ((Number) JsonPath.read(paid, "$.id")).longValue();

		mvc.perform(post("/api/payments/" + paymentId + "/refund")
						.header("Authorization", "Bearer " + TestTokens.admin()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("REFUNDED"));

		verify(reservationClient).cancelBooking(bookingId);
	}

	@Test
	void refundIsAdminOnlyAndOnlyForSuccessfulPayments() throws Exception {
		long bookingId = givenBookingAwaitingPayment("100.00");

		mvc.perform(post("/api/payments")
						.header("Authorization", "Bearer " + TestTokens.customer(CUSTOMER_ID))
						.contentType(MediaType.APPLICATION_JSON).content(payBody(bookingId, "0000111122223333")))
				.andExpect(status().isPaymentRequired());

		long failedId = paymentRepository.findByBookingIdOrderByCreatedAtDesc(bookingId).get(0).getId();

		mvc.perform(post("/api/payments/" + failedId + "/refund")
						.header("Authorization", "Bearer " + TestTokens.customer(CUSTOMER_ID)))
				.andExpect(status().isForbidden());

		mvc.perform(post("/api/payments/" + failedId + "/refund")
						.header("Authorization", "Bearer " + TestTokens.admin()))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value("Only successful payments can be refunded"));
	}
}

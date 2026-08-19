package org.rentez.reservationservice;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.rentez.reservationservice.client.CatalogClient;
import org.rentez.reservationservice.client.InternalCarView;
import org.rentez.reservationservice.client.NotificationClient;
import org.rentez.reservationservice.domain.OutboxEvent;
import org.rentez.reservationservice.repository.OutboxEventRepository;
import org.rentez.reservationservice.service.BookingService;
import org.rentez.reservationservice.service.OutboxRelay;
import org.rentez.reservationservice.web.dto.BookingRequest;
import org.rentez.reservationservice.web.dto.BookingResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

/**
 * The transactional outbox, end to end within reservation.
 *
 * <p>What is being proven is that a cancellation and its pending notification
 * commit together, and that delivery happens afterwards and can fail without
 * touching the booking. In the monolith the equivalent code was
 * {@code notificationService.bookingCancelled(saved)} called inline, so a
 * notification failure propagated straight back into the cancellation.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class OutboxRelayTest {

	private static final long CAR_ID = 8800L;

	@Autowired
	private BookingService bookingService;

	@Autowired
	private OutboxRelay relay;

	@Autowired
	private OutboxEventRepository outboxRepository;

	@MockitoBean
	private CatalogClient catalogClient;

	@MockitoBean
	private NotificationClient notificationClient;

	private BookingResponse bookAndCancel(long customerId, LocalDate start, LocalDate end) {
		given(catalogClient.getCar(anyLong())).willReturn(new InternalCarView(
				CAR_ID, "Toyota", "Corolla", 2023, "SEDAN", "Jurong", new BigDecimal("80.00"), true));

		BookingResponse booking = bookingService.create(customerId, "outbox" + customerId + "@example.com",
				new BookingRequest(CAR_ID, start, end, "Jurong"));
		return bookingService.cancelOwn(customerId, "outbox" + customerId + "@example.com", booking.id());
	}

	@Test
	void cancellingWritesAPendingEventAndTheRelayDeliversIt() throws Exception {
		willDoNothing().given(notificationClient).send(anyString(), anyString());

		BookingResponse cancelled = bookAndCancel(9001L,
				LocalDate.of(2030, 1, 1), LocalDate.of(2030, 1, 3));
		assertThat(cancelled.status().name()).isEqualTo("CANCELLED");

		// The event exists and is PENDING before anything is delivered - it was
		// committed by the same transaction that cancelled the booking.
		List<OutboxEvent> pending = outboxRepository.findByStatusOrderByCreatedAtAsc(
				OutboxEvent.Status.PENDING, org.springframework.data.domain.PageRequest.of(0, 50));
		assertThat(pending).isNotEmpty();

		int delivered = relay.dispatchPending();
		assertThat(delivered).isPositive();

		verify(notificationClient, atLeastOnce()).send(anyString(), anyString());
		assertThat(outboxRepository.countByStatus(OutboxEvent.Status.PENDING)).isZero();
		assertThat(outboxRepository.countByStatus(OutboxEvent.Status.DISPATCHED)).isPositive();
	}

	/**
	 * A notification outage must not lose the event or affect the booking. The row
	 * stays PENDING, its attempt counter moves, and the next tick tries again.
	 */
	@Test
	void aFailedDeliveryLeavesTheEventPendingForRetry() throws Exception {
		willThrow(new RestClientException("notification-service is down"))
				.given(notificationClient).send(anyString(), anyString());

		BookingResponse cancelled = bookAndCancel(9002L,
				LocalDate.of(2030, 2, 1), LocalDate.of(2030, 2, 3));

		// The cancellation succeeded regardless of notification being unreachable.
		assertThat(cancelled.status().name()).isEqualTo("CANCELLED");

		int delivered = relay.dispatchPending();
		assertThat(delivered).isZero();

		List<OutboxEvent> stillPending = outboxRepository.findByStatusOrderByCreatedAtAsc(
				OutboxEvent.Status.PENDING, org.springframework.data.domain.PageRequest.of(0, 50));
		assertThat(stillPending).isNotEmpty();
		assertThat(stillPending.get(0).getAttemptCount()).isPositive();
		assertThat(stillPending.get(0).getLastError()).contains("notification-service is down");

		// Recovery: once the far side is back, the same event goes out.
		willDoNothing().given(notificationClient).send(anyString(), anyString());
		assertThat(relay.dispatchPending()).isPositive();
		assertThat(outboxRepository.countByStatus(OutboxEvent.Status.PENDING)).isZero();
	}

	/**
	 * Pins the wire format against notification-service's
	 * {@code NotificationEventRequest}.
	 *
	 * <p>Both sides are otherwise tested in isolation - the relay against a mocked
	 * client, the consumer against hand-written JSON - so nothing else would catch
	 * the two drifting apart. Every field asserted here is {@code @NotNull} or
	 * {@code @NotBlank} on the consumer, so a rename on either side turns into a
	 * 400 for every notification in production.
	 *
	 * <p>Asserted by parsing rather than by string matching, because the payload
	 * does <em>not</em> survive storage byte for byte: the column is MySQL's native
	 * {@code JSON} type, which normalises the document on write - it reorders keys
	 * and inserts a space after every colon. The value is semantically identical,
	 * which is all that matters, but any test comparing raw text will fail on the
	 * reformatting rather than on a real defect.
	 */
	@Test
	void theEventPayloadMatchesWhatNotificationServiceAccepts() throws Exception {
		willDoNothing().given(notificationClient).send(anyString(), anyString());

		bookAndCancel(9004L, LocalDate.of(2030, 4, 1), LocalDate.of(2030, 4, 2));
		relay.dispatchPending();

		ArgumentCaptor<String> eventId = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
		verify(notificationClient, atLeastOnce()).send(eventId.capture(), payload.capture());

		DocumentContext json = JsonPath.parse(payload.getValue());

		// The id in the body must be the id on the row: that is what the consumer
		// de-duplicates on, and the two disagreeing would defeat the whole scheme.
		assertThat(json.read("$.eventId", String.class)).isEqualTo(eventId.getValue());

		assertThat(json.read("$.recipientId", Long.class)).isEqualTo(9004L);
		assertThat(json.read("$.recipientEmail", String.class)).isEqualTo("outbox9004@example.com");
		assertThat(json.read("$.type", String.class)).isEqualTo("BOOKING_CANCELLED");
		assertThat(json.read("$.message", String.class)).contains("has been cancelled");
		assertThat(json.read("$.relatedEntityType", String.class)).isEqualTo("BOOKING");
		assertThat(json.read("$.relatedEntityId", Long.class)).isNotNull();
	}

	/** Delivered events are not sent twice on the next tick. */
	@Test
	void dispatchingIsNotRepeatedForAlreadyDeliveredEvents() throws Exception {
		willDoNothing().given(notificationClient).send(anyString(), anyString());

		bookAndCancel(9003L, LocalDate.of(2030, 3, 1), LocalDate.of(2030, 3, 2));
		relay.dispatchPending();

		assertThat(relay.dispatchPending())
				.as("a second tick has nothing left to deliver")
				.isZero();
	}
}

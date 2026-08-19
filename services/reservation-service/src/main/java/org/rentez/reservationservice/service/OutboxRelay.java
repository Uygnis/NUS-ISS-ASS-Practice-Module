package org.rentez.reservationservice.service;

import org.rentez.reservationservice.domain.OutboxEvent;
import org.rentez.reservationservice.repository.OutboxEventRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Drains the outbox to notification-service.
 *
 * <p>An outbox nobody reads is dead weight that grows forever, so this is the
 * half that makes the pattern real. The business transaction commits the booking
 * and its pending event together; this relay then delivers them, at-least-once,
 * off the request path. A notification outage can no longer fail a cancellation -
 * which it could in the monolith, where {@code BookingService} called
 * {@code notificationService.bookingCancelled(...)} inline.
 *
 * <p>Delivery is at-least-once rather than exactly-once, and that is a
 * deliberate choice rather than a limitation: if the send succeeds but the row is
 * not marked, the event goes again, and the consumer's unique index on
 * {@code eventId} makes the redelivery a no-op. Trying for exactly-once here
 * would mean a distributed transaction across HTTP.
 *
 * <p>Swapping HTTP for SQS later replaces the client and nothing else. The table,
 * the event id and the de-duplication are the parts that would otherwise have to
 * be retrofitted afterwards.
 */
@Component
public class OutboxRelay {

	private static final int BATCH_SIZE = 50;

	private final OutboxEventRepository outboxRepository;
	private final OutboxDispatcher dispatcher;

	public OutboxRelay(OutboxEventRepository outboxRepository, OutboxDispatcher dispatcher) {
		this.outboxRepository = outboxRepository;
		this.dispatcher = dispatcher;
	}

	/** Returns how many events were delivered, so tests and metrics can assert on it. */
	public int dispatchPending() {
		List<OutboxEvent> pending = outboxRepository.findByStatusOrderByCreatedAtAsc(
				OutboxEvent.Status.PENDING, PageRequest.of(0, BATCH_SIZE));

		int delivered = 0;
		for (OutboxEvent event : pending) {
			// Through the dispatcher bean, so each event really does get its own
			// transaction - see OutboxDispatcher for why this cannot be inlined.
			if (dispatcher.dispatchOne(event.getId())) {
				delivered++;
			}
		}
		return delivered;
	}
}

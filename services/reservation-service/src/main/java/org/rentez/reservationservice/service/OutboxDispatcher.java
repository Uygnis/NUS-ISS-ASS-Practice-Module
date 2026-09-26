package org.rentez.reservationservice.service;

import org.rentez.reservationservice.client.NotificationClient;
import org.rentez.reservationservice.domain.OutboxEvent;
import org.rentez.reservationservice.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Delivers exactly one outbox event, holding no database connection while it
 * waits on the network.
 *
 * <p>This used to be one {@code REQUIRES_NEW} transaction around the whole
 * delivery, HTTP call included, so the relay held a pooled connection for as
 * long as notification-service took to answer. With a pool of 3 that was a third
 * of the service's database capacity spent waiting on another service. Now the
 * read and the status update are each a short repository transaction of their
 * own, and the HTTP call sits between them with no transaction open.
 *
 * <p>Each event is still independent: a failure on one never undoes another's
 * recorded delivery. Delivery is at-least-once as before - if the process dies
 * between the send and the update, the next tick resends, and the consumer's
 * unique index on event_id absorbs the duplicate.
 *
 * <p>Kept as a separate bean from {@link OutboxRelay} so the relay's loop stays
 * free of delivery details.
 */
@Component
public class OutboxDispatcher {

	private static final Logger log = LoggerFactory.getLogger(OutboxDispatcher.class);

	private final OutboxEventRepository outboxRepository;
	private final NotificationClient notificationClient;

	public OutboxDispatcher(OutboxEventRepository outboxRepository, NotificationClient notificationClient) {
		this.outboxRepository = outboxRepository;
		this.notificationClient = notificationClient;
	}

	public boolean dispatchOne(Long id) {
		OutboxEvent event = outboxRepository.findById(id).orElse(null);
		if (event == null || event.getStatus() != OutboxEvent.Status.PENDING) {
			return false;
		}

		try {
			notificationClient.send(event.getEventId(), event.getPayload());
			event.markDispatched();
			outboxRepository.save(event);
			return true;
		}
		catch (Exception ex) {
			// Left PENDING deliberately. The next tick retries, and the consumer's
			// unique index on event_id absorbs the duplicate if the delivery in
			// fact succeeded and only the response was lost.
			event.markFailed(ex.getMessage());
			outboxRepository.save(event);
			log.warn("Outbox delivery failed for event {} (attempt {}): {}",
					event.getEventId(), event.getAttemptCount(), ex.getMessage());
			return false;
		}
	}
}

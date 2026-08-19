package org.rentez.paymentservice.service;

import org.rentez.paymentservice.client.NotificationClient;
import org.rentez.paymentservice.domain.OutboxEvent;
import org.rentez.paymentservice.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Delivers exactly one outbox event, in its own transaction.
 *
 * <p>A separate bean from {@link OutboxRelay} on purpose, and not an
 * implementation detail that could be folded back in. Spring's
 * {@code @Transactional} is proxy-based, so a call from the relay's loop to a
 * method on itself would bypass the proxy entirely and {@code REQUIRES_NEW}
 * would silently do nothing - every event in the batch would then share one
 * transaction, and a single failure would roll back deliveries that had already
 * succeeded.
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

	@Transactional(propagation = Propagation.REQUIRES_NEW)
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

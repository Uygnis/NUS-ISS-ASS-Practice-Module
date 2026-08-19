package org.rentez.reservationservice.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Ticks the outbox relay.
 *
 * <p>Split from {@link OutboxRelay} so the schedule can be switched off without
 * losing the ability to drain the outbox. Tests set
 * {@code rentez.outbox.relay.enabled=false} and call {@code dispatchPending()}
 * directly - a background timer firing partway through a test turns assertions
 * into a race, and "did the notification arrive yet?" is not something to guess at.
 */
@Component
@ConditionalOnProperty(name = "rentez.outbox.relay.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxRelayScheduler {

	private final OutboxRelay relay;

	public OutboxRelayScheduler(OutboxRelay relay) {
		this.relay = relay;
	}

	@Scheduled(fixedDelayString = "${rentez.outbox.relay.delay-ms:2000}")
	public void tick() {
		relay.dispatchPending();
	}
}

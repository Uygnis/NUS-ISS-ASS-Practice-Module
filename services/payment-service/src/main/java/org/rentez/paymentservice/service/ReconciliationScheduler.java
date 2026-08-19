package org.rentez.paymentservice.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Ticks the reconciliation sweeper.
 *
 * <p>Slower than the outbox relay by design. Unfinished sagas are rare and each
 * pass costs calls to reservation, whereas a stuck notification is cheap to
 * retry. Split from the sweeper so tests can disable the timer and drive
 * {@code sweep()} explicitly.
 */
@Component
@ConditionalOnProperty(name = "rentez.reconciliation.enabled", havingValue = "true", matchIfMissing = true)
public class ReconciliationScheduler {

	private final ReconciliationSweeper sweeper;

	public ReconciliationScheduler(ReconciliationSweeper sweeper) {
		this.sweeper = sweeper;
	}

	@Scheduled(fixedDelayString = "${rentez.reconciliation.delay-ms:30000}")
	public void tick() {
		sweeper.sweep();
	}
}

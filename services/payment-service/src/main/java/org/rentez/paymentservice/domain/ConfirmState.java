package org.rentez.paymentservice.domain;

/**
 * How far the saga got, tracked separately from the money.
 *
 * <p>In the monolith, taking payment and confirming the booking were two writes
 * inside one method against one database, with no transaction around them. Split
 * across services they become two systems, and the interesting states are the
 * ones in between - which is what this enum names so the sweeper can find them.
 */
public enum ConfirmState {

	/** Charged, booking not yet confirmed. The sweeper re-drives these. */
	PENDING,

	/** Charged and the booking is confirmed. Terminal, and the happy path. */
	CONFIRMED,

	/** Charged, confirmation impossible, money given back. Terminal. */
	COMPENSATED,

	/**
	 * Charged, confirmation impossible, and the refund ALSO failed. The sweeper
	 * retries these. Without this state the saga would have no recovery path and
	 * money could sit stranded with nothing looking for it.
	 */
	AWAITING_COMPENSATION,

	/** Never charged - a declined payment has no saga to run. Terminal. */
	NOT_APPLICABLE
}

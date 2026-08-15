package org.rentez.reservationservice.domain;

import java.util.Set;

/** Lifecycle of a booking. Unchanged from the monolith. */
public enum BookingStatus {
	PENDING_PAYMENT,
	CONFIRMED,
	MODIFIED,
	CANCELLED,
	COMPLETED;

	/**
	 * The statuses that hold a car's dates against other bookings.
	 *
	 * <p>Identical to the monolith's {@code BookingService.BLOCKING} list, which
	 * was duplicated in {@code CarService} as a local variable named
	 * {@code blocking} - two copies of one rule in a single codebase. It lives on
	 * the enum now so there is one definition.
	 *
	 * <p>These are also exactly the statuses for which {@code booking_day} rows
	 * exist: leaving this set releases the days in the same transaction.
	 */
	public static final Set<BookingStatus> BLOCKING = Set.of(PENDING_PAYMENT, CONFIRMED, MODIFIED);

	public boolean isBlocking() {
		return BLOCKING.contains(this);
	}
}

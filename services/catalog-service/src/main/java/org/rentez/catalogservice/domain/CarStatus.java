package org.rentez.catalogservice.domain;

/**
 * Whether a car can currently be booked. MAINTENANCE and RETIRED both block it.
 *
 * <p>Never leaves this service. Reservation only ever needs the yes/no answer,
 * which the internal car view exposes as a {@code rentable} boolean, so catalog
 * stays free to add states without coordinating a release.
 */
public enum CarStatus {
	AVAILABLE,
	RENTED,
	MAINTENANCE,
	RETIRED;

	/** The single question other services actually ask about a car. */
	public boolean isRentable() {
		return this == AVAILABLE;
	}
}

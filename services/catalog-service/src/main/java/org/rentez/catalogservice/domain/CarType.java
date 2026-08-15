package org.rentez.catalogservice.domain;

/**
 * Vehicle category.
 *
 * <p>Catalog owns this enum, and it must stay owned. Reservation snapshots the
 * type onto each booking as a plain {@code String}, and the internal car view
 * exposes it as a {@code String} too - deliberately, so that adding a constant
 * here cannot break another service's deserialisation. Mirroring this enum in
 * reservation would create exactly that failure mode.
 */
public enum CarType {
	SEDAN,
	SUV,
	HATCHBACK,
	TRUCK,
	ELECTRIC,
	LUXURY
}

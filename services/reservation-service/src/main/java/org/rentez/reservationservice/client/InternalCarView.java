package org.rentez.reservationservice.client;

import java.math.BigDecimal;

/**
 * Reservation's view of a car, as returned by
 * {@code GET /api/catalog/internal/cars/{id}}.
 *
 * <p>A separate declaration from catalog's record of the same name, on purpose.
 * Sharing the type would need a shared module, which cannot work here: each
 * service's Docker build context is its own directory and runs
 * {@code mvn dependency:go-offline} against Maven Central, so a sibling
 * {@code org.rentez} artifact is invisible to the image build.
 *
 * <p>The duplication is also what keeps the contract honest. {@code type} is a
 * String and the status is reduced to {@code rentable}, so catalog can add a
 * vehicle type or a car status without this record failing to deserialise.
 */
public record InternalCarView(
		Long id,
		String make,
		String model,
		int year,
		String type,
		String location,
		BigDecimal dailyRate,
		boolean rentable) {
}

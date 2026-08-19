package org.rentez.catalogservice.web.dto;

import org.rentez.catalogservice.domain.Car;

import java.math.BigDecimal;

/**
 * What reservation-service sees when it asks about a car. This is the only
 * cross-service contract catalog exposes, and it is deliberately narrow.
 *
 * <p>Two choices here are load-bearing:
 *
 * <ul>
 *   <li><strong>{@code rentable} is a boolean, not a {@code CarStatus}.</strong>
 *       Reservation only ever asks "can this be booked?". Sending the status
 *       would let catalog's state machine leak into another service's logic, and
 *       adding a state such as RESERVED_FOR_VIP would then need a coordinated
 *       release.</li>
 *   <li><strong>{@code type} is a String, not the enum.</strong> A mirrored enum
 *       in reservation would fail to deserialise the first time catalog adds a
 *       constant. A varchar costs nothing and cannot drift.</li>
 * </ul>
 *
 * <p>{@code dailyRate} is here because reservation snapshots it onto the booking
 * at creation time, so the price is frozen and no later read has to call back.
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

	public static InternalCarView from(Car car) {
		return new InternalCarView(
				car.getId(),
				car.getMake(),
				car.getModel(),
				car.getYear(),
				car.getType().name(),
				car.getLocation(),
				car.getDailyRate(),
				car.getStatus().isRentable());
	}
}

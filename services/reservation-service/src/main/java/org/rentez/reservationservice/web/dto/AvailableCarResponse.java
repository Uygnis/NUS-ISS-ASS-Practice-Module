package org.rentez.reservationservice.web.dto;

import org.rentez.reservationservice.client.InternalCarView;

import java.math.BigDecimal;

/**
 * A car that is free over the requested dates.
 *
 * <p>This is the response of the endpoint that used to be
 * {@code GET /api/cars?startDate=&endDate=} in the monolith. It moved to
 * reservation because the filtering needs booking data, and having catalog ask
 * reservation for it would have made the two services mutually dependent.
 */
public record AvailableCarResponse(
		Long carId,
		String make,
		String model,
		int year,
		String type,
		String location,
		BigDecimal dailyRate) {

	public static AvailableCarResponse from(InternalCarView car) {
		return new AvailableCarResponse(
				car.id(),
				car.make(),
				car.model(),
				car.year(),
				car.type(),
				car.location(),
				car.dailyRate());
	}
}

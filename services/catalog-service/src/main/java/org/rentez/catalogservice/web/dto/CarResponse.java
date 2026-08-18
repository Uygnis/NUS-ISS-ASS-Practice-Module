package org.rentez.catalogservice.web.dto;

import org.rentez.catalogservice.domain.Car;
import org.rentez.catalogservice.domain.CarStatus;
import org.rentez.catalogservice.domain.CarType;

import java.math.BigDecimal;

/** The public shape of a car. The monolith returned the entity directly; nothing does now. */
public record CarResponse(
		Long id,
		String make,
		String model,
		int year,
		BigDecimal dailyRate,
		String location,
		CarType type,
		CarStatus status) {

	public static CarResponse from(Car car) {
		return new CarResponse(
				car.getId(),
				car.getMake(),
				car.getModel(),
				car.getYear(),
				car.getDailyRate(),
				car.getLocation(),
				car.getType(),
				car.getStatus());
	}
}

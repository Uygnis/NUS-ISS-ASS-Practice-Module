package org.rentez.reservationservice.web.dto;

import org.rentez.reservationservice.domain.Booking;
import org.rentez.reservationservice.domain.BookingStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * The public shape of a booking.
 *
 * <p>The monolith returned the entity, which - because both {@code @ManyToOne}s
 * were EAGER and there were no Jackson annotations anywhere - inlined the whole
 * {@code User} (BCrypt hash included) and the whole {@code Car} into every
 * booking response. This exposes the snapshot fields instead, so the payload is
 * flat, stable, and leaks nothing.
 */
public record BookingResponse(
		Long id,
		Long customerId,
		Long carId,
		String carMake,
		String carModel,
		String carType,
		BigDecimal dailyRate,
		LocalDate startDate,
		LocalDate endDate,
		String pickupLocation,
		BigDecimal totalAmount,
		BookingStatus status,
		Instant createdAt) {

	public static BookingResponse from(Booking booking) {
		return new BookingResponse(
				booking.getId(),
				booking.getCustomerId(),
				booking.getCarId(),
				booking.getCarMake(),
				booking.getCarModel(),
				booking.getCarType(),
				booking.getDailyRateSnapshot(),
				booking.getStartDate(),
				booking.getEndDate(),
				booking.getPickupLocation(),
				booking.getTotalAmount(),
				booking.getStatus(),
				booking.getCreatedAt());
	}
}

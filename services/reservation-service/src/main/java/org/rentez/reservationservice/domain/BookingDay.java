package org.rentez.reservationservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.springframework.data.domain.Persistable;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;

/**
 * One car, one rented day. The composite primary key {@code (carId, day)} is the
 * double-booking guarantee.
 *
 * <p>The monolith read availability and then inserted, with no transaction and no
 * constraint, so two concurrent requests could both see a car as free and both
 * book it. Rather than re-checking harder, correctness is delegated to a unique
 * index: the second insert simply fails. That is the one component every replica
 * shares and the only one that is genuinely serialisable.
 *
 * <p>Rows exist only while a booking is blocking; cancelling or re-dating deletes
 * them in the same transaction that changes the booking.
 */
@Entity
@Table(name = "booking_day")
@IdClass(BookingDay.Key.class)
public class BookingDay implements Persistable<BookingDay.Key> {

	@Id
	@Column(name = "car_id", nullable = false)
	private Long carId;

	@Id
	@Column(name = "day", nullable = false)
	private LocalDate day;

	@Column(name = "booking_id", nullable = false)
	private Long bookingId;

	protected BookingDay() {
	}

	public BookingDay(Long carId, LocalDate day, Long bookingId) {
		this.carId = carId;
		this.day = day;
		this.bookingId = bookingId;
	}

	public Long getCarId() { return carId; }
	public LocalDate getDay() { return day; }
	public Long getBookingId() { return bookingId; }

	@Override
	public Key getId() {
		return new Key(carId, day);
	}

	/**
	 * Always new. This is what makes the primary key actually reject a
	 * double-booking, and it is not optional.
	 *
	 * <p>The identifier here is assigned rather than generated, so Spring Data's
	 * default {@code isNew()} sees a non-null id, decides the row must already
	 * exist, and calls {@code EntityManager.merge()}. Merge does a SELECT and then
	 * an UPDATE - meaning a second booking for the same car and day would quietly
	 * <em>overwrite</em> the first booking's claim and return success. The
	 * constraint never fires, and two customers hold the same car.
	 *
	 * <p>Declaring the row insert-only forces {@code persist()}, so the duplicate
	 * key raises {@code DataIntegrityViolationException} as intended. These rows
	 * are only ever inserted or deleted; nothing updates one.
	 */
	@Override
	@Transient
	public boolean isNew() {
		return true;
	}

	/** JPA composite-key holder for {@code (carId, day)}. */
	public static class Key implements Serializable {

		private Long carId;
		private LocalDate day;

		public Key() {
		}

		public Key(Long carId, LocalDate day) {
			this.carId = carId;
			this.day = day;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) {
				return true;
			}
			if (!(o instanceof Key other)) {
				return false;
			}
			return Objects.equals(carId, other.carId) && Objects.equals(day, other.day);
		}

		@Override
		public int hashCode() {
			return Objects.hash(carId, day);
		}
	}
}

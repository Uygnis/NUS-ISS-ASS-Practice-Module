package org.rentez.reservationservice.repository;

import org.rentez.reservationservice.domain.BookingDay;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookingDayRepository extends JpaRepository<BookingDay, BookingDay.Key> {

	/**
	 * Releases a booking's held days, in the same transaction as the status change.
	 *
	 * <p>{@code flushAutomatically} pushes any pending entity changes out before
	 * the bulk delete runs, and {@code clearAutomatically} drops the now-stale
	 * persistence context - without it, re-inserting the same
	 * {@code (carId, day)} pair during a modify would collide with rows JPA still
	 * believes are present.
	 */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("delete from BookingDay d where d.bookingId = :bookingId")
	int deleteByBookingId(@Param("bookingId") Long bookingId);

	long countByBookingId(Long bookingId);
}

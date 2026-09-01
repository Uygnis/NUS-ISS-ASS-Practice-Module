package com.CarRental_NUSISS.CarRental_NUSISS;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.List;

public interface BookingRepository extends JpaRepository<Booking, Long> {

	List<Booking> findByCustomerIdOrderByCreatedAtDesc(Long customerId);

	/** Car ids with a "blocking" booking (pending/confirmed/modified) overlapping the given range. */
	@Query("""
			select b.car.id from Booking b
			where b.status in :statuses
			and b.startDate <= :endDate and b.endDate >= :startDate
			""")
	List<Long> findBookedCarIds(@Param("statuses") List<Booking.BookingStatus> statuses,
			@Param("startDate") LocalDate startDate,
			@Param("endDate") LocalDate endDate);

	/** Overlapping bookings for one specific car, excluding a given booking id (used when modifying). */
	@Query("""
			select b from Booking b
			where b.car.id = :carId and b.id <> :excludeBookingId
			and b.status in :statuses
			and b.startDate <= :endDate and b.endDate >= :startDate
			""")
	List<Booking> findOverlapping(@Param("carId") Long carId,
			@Param("excludeBookingId") Long excludeBookingId,
			@Param("statuses") List<Booking.BookingStatus> statuses,
			@Param("startDate") LocalDate startDate,
			@Param("endDate") LocalDate endDate);

	long countByStatus(Booking.BookingStatus status);
}

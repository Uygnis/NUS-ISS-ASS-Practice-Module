package org.rentez.reservationservice.repository;

import org.rentez.reservationservice.domain.Booking;
import org.rentez.reservationservice.domain.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface BookingRepository extends JpaRepository<Booking, Long> {

	List<Booking> findByCustomerIdOrderByCreatedAtDesc(Long customerId);

	long countByStatus(BookingStatus status);

	/**
	 * Car ids with a blocking booking overlapping the given range.
	 *
	 * <p>Ported almost verbatim - the only change is {@code b.car.id} becoming
	 * {@code b.carId}. It survived because it already projected to a
	 * {@code List<Long>} rather than returning {@code Car} objects: it was an
	 * identifier-based contract before anyone intended it as one, which is
	 * exactly the shape a service boundary needs.
	 *
	 * <p>Still used for the availability search. It is NOT what prevents
	 * double-booking - a read cannot do that. See {@code booking_day}.
	 */
	@Query("""
			select b.carId from Booking b
			where b.status in :statuses
			and b.startDate <= :endDate and b.endDate >= :startDate
			""")
	List<Long> findBookedCarIds(@Param("statuses") Collection<BookingStatus> statuses,
			@Param("startDate") LocalDate startDate,
			@Param("endDate") LocalDate endDate);

	/** Booking counts by the snapshotted car type - the local replacement for ReportService's groupBy. */
	@Query("""
			select b.carType, count(b) from Booking b
			group by b.carType
			""")
	List<Object[]> countGroupedByCarType();
}

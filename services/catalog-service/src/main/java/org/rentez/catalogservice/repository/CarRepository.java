package org.rentez.catalogservice.repository;

import org.rentez.catalogservice.domain.Car;
import org.rentez.catalogservice.domain.CarStatus;
import org.rentez.catalogservice.domain.CarType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CarRepository extends JpaRepository<Car, Long> {

	List<Car> findByStatus(CarStatus status);

	long countByStatus(CarStatus status);

	/**
	 * Cars with the given status, optionally narrowed by location and type.
	 *
	 * <p>Single-entity and join-free, which is why it ported unchanged. The
	 * date-range half of the monolith's search did not: filtering out cars that
	 * are already booked needs reservation's data, so that logic now lives in
	 * reservation-service, which calls this service for candidates.
	 */
	@Query("""
			select c from Car c
			where c.status = :status
			and (:location is null or lower(c.location) = lower(:location))
			and (:type is null or c.type = :type)
			""")
	List<Car> findByFilters(@Param("status") CarStatus status,
			@Param("location") String location,
			@Param("type") CarType type);
}

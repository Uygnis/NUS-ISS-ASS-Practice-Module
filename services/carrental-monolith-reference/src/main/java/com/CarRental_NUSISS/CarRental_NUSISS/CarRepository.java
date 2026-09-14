package com.CarRental_NUSISS.CarRental_NUSISS;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface CarRepository extends JpaRepository<Car, Long> {

	List<Car> findByStatus(Car.CarStatus status);

	/**
	 * Cars with the given status, optionally narrowed by location/type. Date-range
	 * availability is filtered afterwards in CarService (see findBookedCarIds) -
	 * keeping that logic in Java rather than JPQL keeps the enum handling simple.
	 */
	@Query("""
			select c from Car c
			where c.status = :status
			and (:location is null or lower(c.location) = lower(:location))
			and (:type is null or c.type = :type)
			""")
	List<Car> findByFilters(@Param("status") Car.CarStatus status,
			@Param("location") String location,
			@Param("type") Car.CarType type);
}

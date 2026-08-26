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
	 * <p>Single-entity and join-free, which is why it ported unchanged from the
	 * monolith. The date-range half of the monolith's search did not: filtering
	 * out cars that are already booked needs reservation's data, so that logic
	 * now lives in reservation-service, which calls this service for candidates.
	 *
	 * <p><b>The {@code cast(:location as String)} is load-bearing on PostgreSQL.</b>
	 * Both filters are optional, so this query is usually called with a null
	 * location. Hibernate cannot infer a type for a parameter whose only context
	 * is {@code :location is null}, binds it as an untyped object, and the
	 * PostgreSQL driver sends it as {@code bytea} - at which point the planner
	 * rejects the whole statement with "function lower(bytea) does not exist",
	 * because Postgres resolves types for every branch of an OR at parse time,
	 * not only the branch it will evaluate. MySQL coerced silently and this read
	 * as portable code for as long as MySQL was the only target.
	 *
	 * <p>Nothing caught it for a while because {@code CarController} short-circuits
	 * the both-null case to {@code findByStatus}, so the public browse endpoint
	 * never reaches this query. The only caller that does is the INTERNAL
	 * rentable list, which reservation-service calls with both filters null on
	 * every availability search - see {@code internalRentableListAcceptsNoFilters}.
	 */
	@Query("""
			select c from Car c
			where c.status = :status
			and (cast(:location as String) is null
			     or lower(c.location) = lower(cast(:location as String)))
			and (:type is null or c.type = :type)
			""")
	List<Car> findByFilters(@Param("status") CarStatus status,
			@Param("location") String location,
			@Param("type") CarType type);
}

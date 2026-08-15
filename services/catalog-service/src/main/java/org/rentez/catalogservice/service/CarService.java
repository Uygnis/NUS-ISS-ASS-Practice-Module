package org.rentez.catalogservice.service;

import org.rentez.catalogservice.domain.Car;
import org.rentez.catalogservice.domain.CarStatus;
import org.rentez.catalogservice.domain.CarType;
import org.rentez.catalogservice.error.ApiException;
import org.rentez.catalogservice.repository.CarRepository;
import org.rentez.catalogservice.web.dto.CarRequest;
import org.rentez.catalogservice.web.dto.CarResponse;
import org.rentez.catalogservice.web.dto.CatalogStats;
import org.rentez.catalogservice.web.dto.InternalCarView;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

/**
 * Fleet management and browsing.
 *
 * <p>The important thing about this class is what it no longer has. In the
 * monolith {@code CarService} injected {@code BookingRepository} so that
 * {@code search} could filter out cars already booked over a date range. That
 * made catalog depend on reservation while reservation already depended on
 * catalog for car details and pricing - a cycle, and one that would have meant
 * neither service could be built, tested or deployed alone.
 *
 * <p>The date-range filter moved to reservation-service, which owns the booking
 * data and calls this service for candidates. Catalog now has <strong>zero
 * outbound runtime dependencies</strong>, which is worth more than keeping the
 * original API shape. The cost is that
 * {@code GET /api/cars?startDate=&endDate=} splits into two endpoints on two
 * services - free today, because the frontend has no API client at all.
 */
@Service
public class CarService {

	/** Staff may take a car out of service and put it back, nothing more. */
	private static final Set<CarStatus> STAFF_ASSIGNABLE = Set.of(CarStatus.AVAILABLE, CarStatus.MAINTENANCE);

	private final CarRepository carRepository;
	private final AuditService auditService;

	public CarService(CarRepository carRepository, AuditService auditService) {
		this.carRepository = carRepository;
		this.auditService = auditService;
	}

	@Transactional(readOnly = true)
	public List<CarResponse> browseAvailable() {
		return carRepository.findByStatus(CarStatus.AVAILABLE).stream().map(CarResponse::from).toList();
	}

	/** Browse by location and/or type. Date-range availability lives in reservation-service. */
	@Transactional(readOnly = true)
	public List<CarResponse> search(String location, CarType type) {
		return carRepository.findByFilters(CarStatus.AVAILABLE, location, type).stream()
				.map(CarResponse::from)
				.toList();
	}

	@Transactional(readOnly = true)
	public CarResponse getById(Long id) {
		return CarResponse.from(requireCar(id));
	}

	/** The cross-service view: reservation asks this before accepting a booking. */
	@Transactional(readOnly = true)
	public InternalCarView getInternalView(Long id) {
		return InternalCarView.from(requireCar(id));
	}

	/**
	 * Rentable candidates for a location/type, as reservation sees them.
	 *
	 * <p>This is the first half of what {@code CarService.search} used to do in one
	 * process. Reservation calls it and then subtracts the cars it knows are
	 * already booked over the requested dates - the half that needs booking data.
	 * Exposed on the internal contract rather than reusing the public browse
	 * endpoint so that cross-service callers never see catalog's enums.
	 */
	@Transactional(readOnly = true)
	public List<InternalCarView> findRentable(String location, CarType type) {
		return carRepository.findByFilters(CarStatus.AVAILABLE, location, type).stream()
				.map(InternalCarView::from)
				.toList();
	}

	@Transactional(readOnly = true)
	public CatalogStats stats() {
		return new CatalogStats(
				carRepository.count(),
				carRepository.countByStatus(CarStatus.AVAILABLE),
				carRepository.countByStatus(CarStatus.MAINTENANCE));
	}

	@Transactional
	public CarResponse create(CarRequest request, String actorEmail) {
		Car saved = carRepository.save(new Car(request.make(), request.model(), request.year(),
				request.dailyRate(), request.location(), request.type()));

		auditService.log(actorEmail, "CREATE_CAR", "Car", saved.getId(), saved.getMake() + " " + saved.getModel());
		return CarResponse.from(saved);
	}

	@Transactional
	public CarResponse update(Long id, CarRequest request, String actorEmail) {
		Car car = requireCar(id);
		car.setMake(request.make());
		car.setModel(request.model());
		car.setYear(request.year());
		car.setDailyRate(request.dailyRate());
		car.setLocation(request.location());
		car.setType(request.type());
		Car saved = carRepository.save(car);

		auditService.log(actorEmail, "UPDATE_CAR", "Car", id, null);
		return CarResponse.from(saved);
	}

	@Transactional
	public void delete(Long id, String actorEmail) {
		Car car = requireCar(id);
		carRepository.delete(car);
		auditService.log(actorEmail, "DELETE_CAR", "Car", id, car.getMake() + " " + car.getModel());
	}

	/**
	 * Admins may set any status; staff are limited to AVAILABLE and MAINTENANCE.
	 *
	 * <p><strong>Behaviour change, made deliberately.</strong> Both the monolith's
	 * controller and its service documented this restriction and neither enforced
	 * it, so any STAFF account could RETIRE a car. Porting the comment without the
	 * check would carry a real privilege gap into the new service, so the check is
	 * now here - in the service, where it cannot be bypassed by a second caller.
	 */
	@Transactional
	public CarResponse setStatus(Long id, CarStatus status, String actorEmail, boolean actorIsAdmin) {
		if (!actorIsAdmin && !STAFF_ASSIGNABLE.contains(status)) {
			throw new ApiException(HttpStatus.FORBIDDEN, "Staff may only set a car to AVAILABLE or MAINTENANCE");
		}
		Car car = requireCar(id);
		car.setStatus(status);
		Car saved = carRepository.save(car);

		auditService.log(actorEmail, "SET_CAR_STATUS", "Car", id, "New status: " + status);
		return CarResponse.from(saved);
	}

	private Car requireCar(Long id) {
		return carRepository.findById(id)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "No car with id " + id));
	}
}

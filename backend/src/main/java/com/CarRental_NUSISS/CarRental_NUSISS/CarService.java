package com.CarRental_NUSISS.CarRental_NUSISS;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.util.List;

@Service
public class CarService {

	private final CarRepository carRepository;
	private final BookingRepository bookingRepository;
	private final AuditService auditService;

	public CarService(CarRepository carRepository, BookingRepository bookingRepository, AuditService auditService) {
		this.carRepository = carRepository;
		this.bookingRepository = bookingRepository;
		this.auditService = auditService;
	}

	public List<Car> browseAvailable() {
		return carRepository.findByStatus(Car.CarStatus.AVAILABLE);
	}

	/** Search by any combination of location, vehicle type and a wanted date range. */
	public List<Car> search(String location, Car.CarType type, LocalDate startDate, LocalDate endDate) {
		List<Car> candidates = carRepository.findByFilters(Car.CarStatus.AVAILABLE, location, type);

		if (startDate == null || endDate == null) {
			return candidates;
		}
		if (endDate.isBefore(startDate)) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "endDate cannot be before startDate");
		}

		List<Booking.BookingStatus> blocking = List.of(
				Booking.BookingStatus.PENDING_PAYMENT, Booking.BookingStatus.CONFIRMED, Booking.BookingStatus.MODIFIED);
		List<Long> bookedCarIds = bookingRepository.findBookedCarIds(blocking, startDate, endDate);

		return candidates.stream().filter(c -> !bookedCarIds.contains(c.getId())).toList();
	}

	public Car getById(Long id) {
		return carRepository.findById(id)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "No car with id " + id));
	}

	public Car create(CarRequest request, String actorEmail) {
		Car car = new Car(request.make(), request.model(), request.year(), request.dailyRate(),
				request.location(), request.type());
		Car saved = carRepository.save(car);
		auditService.log(actorEmail, "CREATE_CAR", "Car", saved.getId(), saved.getMake() + " " + saved.getModel());
		return saved;
	}

	public Car update(Long id, CarRequest request, String actorEmail) {
		Car car = getById(id);
		car.setMake(request.make());
		car.setModel(request.model());
		car.setYear(request.year());
		car.setDailyRate(request.dailyRate());
		car.setLocation(request.location());
		car.setType(request.type());
		Car saved = carRepository.save(car);
		auditService.log(actorEmail, "UPDATE_CAR", "Car", id, null);
		return saved;
	}

	public void delete(Long id, String actorEmail) {
		Car car = getById(id);
		carRepository.delete(car);
		auditService.log(actorEmail, "DELETE_CAR", "Car", id, car.getMake() + " " + car.getModel());
	}

	/** Used by admins (any status) and by staff (limited to AVAILABLE/MAINTENANCE, enforced at the controller). */
	public Car setStatus(Long id, Car.CarStatus status, String actorEmail) {
		Car car = getById(id);
		car.setStatus(status);
		Car saved = carRepository.save(car);
		auditService.log(actorEmail, "SET_CAR_STATUS", "Car", id, "New status: " + status);
		return saved;
	}
}

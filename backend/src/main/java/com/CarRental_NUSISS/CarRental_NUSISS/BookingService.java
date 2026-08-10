package com.CarRental_NUSISS.CarRental_NUSISS;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.util.List;

@Service
public class BookingService {

	private static final List<Booking.BookingStatus> BLOCKING = List.of(
			Booking.BookingStatus.PENDING_PAYMENT, Booking.BookingStatus.CONFIRMED, Booking.BookingStatus.MODIFIED);

	private final BookingRepository bookingRepository;
	private final CarRepository carRepository;
	private final AuditService auditService;
	private final NotificationService notificationService;

	public BookingService(BookingRepository bookingRepository, CarRepository carRepository,
			AuditService auditService, NotificationService notificationService) {
		this.bookingRepository = bookingRepository;
		this.carRepository = carRepository;
		this.auditService = auditService;
		this.notificationService = notificationService;
	}

	public Booking create(User customer, BookingRequest request) {
		if (request.endDate().isBefore(request.startDate())) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "endDate cannot be before startDate");
		}
		Car car = carRepository.findById(request.carId())
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "No car with id " + request.carId()));
		if (car.getStatus() != Car.CarStatus.AVAILABLE) {
			throw new ApiException(HttpStatus.CONFLICT, "This car is not currently rentable");
		}

		List<Long> bookedCarIds = bookingRepository.findBookedCarIds(BLOCKING, request.startDate(), request.endDate());
		if (bookedCarIds.contains(car.getId())) {
			throw new ApiException(HttpStatus.CONFLICT, "This car is already booked for part of that date range");
		}

		BigDecimal total = priceFor(car, request.startDate().datesUntil(request.endDate().plusDays(1)).count());
		Booking booking = new Booking(customer, car, request.startDate(), request.endDate(),
				request.pickupLocation(), total);
		Booking saved = bookingRepository.save(booking);
		auditService.log(customer.getEmail(), "CREATE_BOOKING", "Booking", saved.getId(),
				"Car #" + car.getId() + " " + request.startDate() + " to " + request.endDate());
		return saved;
	}

	public Booking modify(User customer, Long bookingId, BookingUpdateRequest request) {
		Booking booking = getOwned(customer, bookingId);
		if (booking.getStatus() == Booking.BookingStatus.CANCELLED || booking.getStatus() == Booking.BookingStatus.COMPLETED) {
			throw new ApiException(HttpStatus.CONFLICT, "Cannot modify a " + booking.getStatus() + " booking");
		}
		if (request.endDate().isBefore(request.startDate())) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "endDate cannot be before startDate");
		}

		List<Booking> overlapping = bookingRepository.findOverlapping(
				booking.getCar().getId(), booking.getId(), BLOCKING, request.startDate(), request.endDate());
		if (!overlapping.isEmpty()) {
			throw new ApiException(HttpStatus.CONFLICT, "This car is already booked for part of that date range");
		}

		booking.setStartDate(request.startDate());
		booking.setEndDate(request.endDate());
		booking.setPickupLocation(request.pickupLocation());
		booking.setTotalAmount(priceFor(booking.getCar(),
				request.startDate().datesUntil(request.endDate().plusDays(1)).count()));
		if (booking.getStatus() == Booking.BookingStatus.CONFIRMED) {
			booking.setStatus(Booking.BookingStatus.MODIFIED);
		}

		Booking saved = bookingRepository.save(booking);
		auditService.log(customer.getEmail(), "MODIFY_BOOKING", "Booking", bookingId, null);
		return saved;
	}

	public Booking cancel(User actor, Long bookingId) {
		Booking booking = actor.getRole() == User.Role.CUSTOMER ? getOwned(actor, bookingId) : getById(bookingId);
		if (booking.getStatus() == Booking.BookingStatus.CANCELLED) {
			return booking;
		}
		if (booking.getStatus() == Booking.BookingStatus.COMPLETED) {
			throw new ApiException(HttpStatus.CONFLICT, "Cannot cancel a completed booking");
		}
		booking.setStatus(Booking.BookingStatus.CANCELLED);
		Booking saved = bookingRepository.save(booking);
		notificationService.bookingCancelled(saved);
		auditService.log(actor.getEmail(), "CANCEL_BOOKING", "Booking", bookingId, null);
		return saved;
	}

	public List<Booking> historyFor(Long customerId) {
		return bookingRepository.findByCustomerIdOrderByCreatedAtDesc(customerId);
	}

	public Booking getById(Long id) {
		return bookingRepository.findById(id)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "No booking with id " + id));
	}

	public Booking getOwned(User customer, Long id) {
		Booking booking = getById(id);
		if (!booking.getCustomer().getId().equals(customer.getId())) {
			throw new ApiException(HttpStatus.FORBIDDEN, "This booking does not belong to you");
		}
		return booking;
	}

	private BigDecimal priceFor(Car car, long inclusiveDays) {
		long days = Math.max(inclusiveDays, 1);
		return car.getDailyRate().multiply(BigDecimal.valueOf(days));
	}
}

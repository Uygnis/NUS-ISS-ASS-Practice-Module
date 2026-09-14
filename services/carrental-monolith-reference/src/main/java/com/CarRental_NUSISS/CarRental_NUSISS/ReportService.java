package com.CarRental_NUSISS.CarRental_NUSISS;

import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Simple aggregate metrics for the admin dashboard. Fine for a prototype's data volume;
 * swap for DB-side aggregation (native queries / a reporting view) once the fleet is large. */
@Service
public class ReportService {

	private final CarRepository carRepository;
	private final BookingRepository bookingRepository;
	private final PaymentRepository paymentRepository;

	public ReportService(CarRepository carRepository, BookingRepository bookingRepository,
			PaymentRepository paymentRepository) {
		this.carRepository = carRepository;
		this.bookingRepository = bookingRepository;
		this.paymentRepository = paymentRepository;
	}

	public ReportSummary summary() {
		List<Car> cars = carRepository.findAll();
		List<Booking> bookings = bookingRepository.findAll();

		BigDecimal revenue = paymentRepository.findAll().stream()
				.filter(p -> p.getStatus() == Payment.Status.SUCCESS)
				.map(Payment::getAmount)
				.reduce(BigDecimal.ZERO, BigDecimal::add);

		Map<String, Long> byType = bookings.stream()
				.collect(Collectors.groupingBy(b -> b.getCar().getType().name(), Collectors.counting()));

		return new ReportSummary(
				cars.size(),
				cars.stream().filter(c -> c.getStatus() == Car.CarStatus.AVAILABLE).count(),
				cars.stream().filter(c -> c.getStatus() == Car.CarStatus.MAINTENANCE).count(),
				bookings.size(),
				bookings.stream().filter(b -> b.getStatus() == Booking.BookingStatus.CONFIRMED).count(),
				bookings.stream().filter(b -> b.getStatus() == Booking.BookingStatus.CANCELLED).count(),
				revenue,
				byType);
	}
}

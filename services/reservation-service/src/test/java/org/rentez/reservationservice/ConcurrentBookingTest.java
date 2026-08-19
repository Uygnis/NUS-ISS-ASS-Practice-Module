package org.rentez.reservationservice;

import org.junit.jupiter.api.Test;
import org.rentez.reservationservice.client.CatalogClient;
import org.rentez.reservationservice.client.InternalCarView;
import org.rentez.reservationservice.domain.BookingStatus;
import org.rentez.reservationservice.repository.BookingRepository;
import org.rentez.reservationservice.service.BookingService;
import org.rentez.reservationservice.web.dto.BookingRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;

/**
 * Proves the double-booking race is actually closed.
 *
 * <p>The monolith's {@code BookingService.create} read availability and then
 * inserted, with no transaction and no unique constraint between the two steps:
 *
 * <pre>
 *   List&lt;Long&gt; bookedCarIds = bookingRepository.findBookedCarIds(...);
 *   if (bookedCarIds.contains(car.getId())) throw ...;   // check
 *   bookingRepository.save(booking);                     // ...then act
 * </pre>
 *
 * <p>Two requests arriving together both read "free" and both insert. Run against
 * that implementation, this test would produce several confirmed bookings for the
 * same car on the same days.
 *
 * <p>Nothing here re-checks harder. The threads race deliberately, and the
 * {@code (car_id, day)} primary key decides the winner - which is the point of
 * delegating the guarantee to the database rather than to application logic.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ConcurrentBookingTest {

	private static final int THREADS = 8;
	private static final long CONTESTED_CAR = 555L;

	@Autowired
	private BookingService bookingService;

	@Autowired
	private BookingRepository bookingRepository;

	@MockitoBean
	private CatalogClient catalogClient;

	@Test
	void onlyOneOfManySimultaneousBookingsForTheSameCarSucceeds() throws Exception {
		given(catalogClient.getCar(anyLong())).willReturn(new InternalCarView(
				CONTESTED_CAR, "Toyota", "Corolla", 2023, "SEDAN", "Jurong",
				new BigDecimal("80.00"), true));

		LocalDate start = LocalDate.of(2029, 5, 1);
		LocalDate end = LocalDate.of(2029, 5, 4);

		// Every thread blocks on the same latch, so they contend on the insert
		// rather than trickling through one after another.
		CountDownLatch startLine = new CountDownLatch(1);
		AtomicInteger succeeded = new AtomicInteger();
		AtomicInteger rejected = new AtomicInteger();

		ExecutorService pool = Executors.newFixedThreadPool(THREADS);
		try {
			List<Callable<Void>> attempts = java.util.stream.IntStream.range(0, THREADS)
					.mapToObj(i -> (Callable<Void>) () -> {
						startLine.await();
						try {
							bookingService.create((long) (100 + i), "racer" + i + "@example.com",
									new BookingRequest(CONTESTED_CAR, start, end, "Jurong"));
							succeeded.incrementAndGet();
						}
						catch (Exception ex) {
							// Any loser is fine; what must not happen is a second
							// success.
							rejected.incrementAndGet();
						}
						return null;
					})
					.toList();

			List<Future<Void>> futures = attempts.stream().map(pool::submit).toList();
			startLine.countDown();
			for (Future<Void> future : futures) {
				future.get(60, TimeUnit.SECONDS);
			}
		}
		finally {
			pool.shutdownNow();
		}

		assertThat(succeeded.get())
				.as("exactly one booking may win the race for a car's dates")
				.isEqualTo(1);
		assertThat(rejected.get()).isEqualTo(THREADS - 1);

		// And the database agrees: one blocking booking holds this car.
		long held = bookingRepository.findBookedCarIds(BookingStatus.BLOCKING, start, end).stream()
				.filter(carId -> carId != null && carId == CONTESTED_CAR)
				.count();
		assertThat(held).isEqualTo(1);
	}
}

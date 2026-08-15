package org.rentez.reservationservice.service;

import org.rentez.reservationservice.client.CatalogClient;
import org.rentez.reservationservice.client.InternalCarView;
import org.rentez.reservationservice.domain.Booking;
import org.rentez.reservationservice.domain.BookingDay;
import org.rentez.reservationservice.domain.BookingStatus;
import org.rentez.reservationservice.error.ApiException;
import org.rentez.reservationservice.repository.BookingDayRepository;
import org.rentez.reservationservice.repository.BookingRepository;
import org.rentez.reservationservice.web.dto.AvailableCarResponse;
import org.rentez.reservationservice.web.dto.BookingRequest;
import org.rentez.reservationservice.web.dto.BookingResponse;
import org.rentez.reservationservice.web.dto.BookingUpdateRequest;
import org.rentez.reservationservice.web.dto.ReservationStats;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Booking lifecycle. The business rules are ported unchanged from the monolith's
 * {@code BookingService}; what changed is where the data comes from and how
 * concurrency is handled.
 */
@Service
public class BookingService {

	private final BookingRepository bookingRepository;
	private final BookingDayRepository bookingDayRepository;
	private final CatalogClient catalogClient;
	private final AuditService auditService;
	private final OutboxWriter outbox;

	public BookingService(BookingRepository bookingRepository, BookingDayRepository bookingDayRepository,
			CatalogClient catalogClient, AuditService auditService, OutboxWriter outbox) {
		this.bookingRepository = bookingRepository;
		this.bookingDayRepository = bookingDayRepository;
		this.catalogClient = catalogClient;
		this.auditService = auditService;
		this.outbox = outbox;
	}

	// ------------------------------------------------------------------ create

	@Transactional
	public BookingResponse create(Long customerId, String customerEmail, BookingRequest request) {
		requireOrderedDates(request.startDate(), request.endDate());

		// Was carRepository.findById(...) in-process. Catalog is still the source
		// of truth for rentability and price; we ask once and then snapshot.
		InternalCarView car = catalogClient.getCar(request.carId());
		if (!car.rentable()) {
			throw new ApiException(HttpStatus.CONFLICT, "This car is not currently rentable");
		}

		BigDecimal total = priceFor(car.dailyRate(), request.startDate(), request.endDate());

		Booking booking = bookingRepository.save(new Booking(
				customerId, customerEmail,
				car.id(), car.make(), car.model(), car.type(), car.dailyRate(),
				request.startDate(), request.endDate(), request.pickupLocation(), total));

		holdDays(booking);

		auditService.log(customerEmail, "CREATE_BOOKING", "Booking", booking.getId(),
				"Car #" + car.id() + " " + request.startDate() + " to " + request.endDate());
		return BookingResponse.from(booking);
	}

	// ------------------------------------------------------------------ modify

	@Transactional
	public BookingResponse modify(Long customerId, String actorEmail, Long bookingId,
			BookingUpdateRequest request) {
		Booking booking = requireOwned(customerId, bookingId);

		if (booking.getStatus() == BookingStatus.CANCELLED || booking.getStatus() == BookingStatus.COMPLETED) {
			throw new ApiException(HttpStatus.CONFLICT, "Cannot modify a " + booking.getStatus() + " booking");
		}
		requireOrderedDates(request.startDate(), request.endDate());

		// Release this booking's own days first, or the new range would collide
		// with the old one through the same primary key. The delete is flushed
		// before the inserts run.
		bookingDayRepository.deleteByBookingId(booking.getId());

		booking.setStartDate(request.startDate());
		booking.setEndDate(request.endDate());
		booking.setPickupLocation(request.pickupLocation());

		// Re-priced from the SNAPSHOT, not from catalog's current rate.
		//
		// Deviation from the monolith, which called priceFor(booking.getCar(), ...)
		// against the live Car and so silently re-quoted a modified booking at
		// whatever the price happened to be that day. Charging a customer a rate
		// they were never shown is the kind of behaviour that only looks correct
		// until someone edits a rate. Holding the snapshot also keeps modify off
		// catalog's availability - a catalog outage cannot block a date change.
		booking.setTotalAmount(priceFor(booking.getDailyRateSnapshot(),
				request.startDate(), request.endDate()));

		if (booking.getStatus() == BookingStatus.CONFIRMED) {
			booking.setStatus(BookingStatus.MODIFIED);
		}

		Booking saved = bookingRepository.save(booking);
		holdDays(saved);

		// The caller's own address from the token, not the booking's snapshot -
		// the monolith audited actor.getEmail(), and the two can differ once a
		// customer changes their email after booking.
		auditService.log(actorEmail, "MODIFY_BOOKING", "Booking", bookingId, null);
		return BookingResponse.from(saved);
	}

	// ------------------------------------------------------------------ cancel

	/** Customer cancelling their own booking. */
	@Transactional
	public BookingResponse cancelOwn(Long customerId, String actorEmail, Long bookingId) {
		return doCancel(requireOwned(customerId, bookingId), actorEmail);
	}

	/**
	 * Cancellation on behalf of the system - used by payment-service when a
	 * refund is issued.
	 *
	 * <p>This is where the monolith's non-customer branch finally lives. Its
	 * {@code cancel} chose {@code getOwned} or {@code getById} on the actor's
	 * role, but {@code BookingController} was annotated
	 * {@code @PreAuthorize("hasRole('CUSTOMER')")} at class level, so the
	 * ownership-free path was unreachable: staff and admin could not cancel
	 * anything. Rather than port dead code or quietly widen who may cancel, the
	 * branch is exposed on the internal API, where the caller is another service.
	 *
	 * <p>Idempotent: cancelling an already-cancelled booking succeeds.
	 */
	@Transactional
	public BookingResponse cancelInternal(Long bookingId, String actorEmail) {
		return doCancel(requireBooking(bookingId), actorEmail);
	}

	private BookingResponse doCancel(Booking booking, String actorEmail) {
		// Already cancelled is a no-op, exactly as before: no second notification
		// and no second audit entry. This is what makes retries safe.
		if (booking.getStatus() == BookingStatus.CANCELLED) {
			return BookingResponse.from(booking);
		}
		if (booking.getStatus() == BookingStatus.COMPLETED) {
			throw new ApiException(HttpStatus.CONFLICT, "Cannot cancel a completed booking");
		}

		booking.setStatus(BookingStatus.CANCELLED);
		Booking saved = bookingRepository.save(booking);

		// The car's days go back on the market in the same transaction that
		// cancels the booking - never one without the other.
		bookingDayRepository.deleteByBookingId(saved.getId());

		outbox.bookingCancelled(saved);
		auditService.log(actorEmail, "CANCEL_BOOKING", "Booking", saved.getId(), null);
		return BookingResponse.from(saved);
	}

	// ----------------------------------------------------------------- confirm

	/**
	 * Marks a booking paid. Called by payment-service after a successful charge;
	 * in the monolith this was {@code booking.setStatus(CONFIRMED)} inline inside
	 * {@code PaymentService.pay}.
	 *
	 * <p><strong>Idempotent by contract.</strong> PENDING_PAYMENT to CONFIRMED and
	 * CONFIRMED to CONFIRMED both succeed. Payment retries this call whenever the
	 * response is lost, and a 409 on an already-confirmed booking would make the
	 * retry look like a failure and trigger a refund for a booking that is in fact
	 * paid and confirmed.
	 */
	@Transactional
	public BookingResponse confirm(Long bookingId, String actorEmail) {
		Booking booking = requireBooking(bookingId);

		if (booking.getStatus() == BookingStatus.CONFIRMED) {
			return BookingResponse.from(booking);
		}
		if (booking.getStatus() != BookingStatus.PENDING_PAYMENT
				&& booking.getStatus() != BookingStatus.MODIFIED) {
			throw new ApiException(HttpStatus.CONFLICT,
					"Cannot confirm a " + booking.getStatus() + " booking");
		}

		booking.setStatus(BookingStatus.CONFIRMED);
		Booking saved = bookingRepository.save(booking);

		outbox.bookingConfirmed(saved);
		auditService.log(actorEmail, "CONFIRM_BOOKING", "Booking", saved.getId(), null);
		return BookingResponse.from(saved);
	}

	// ------------------------------------------------------------------- reads

	@Transactional(readOnly = true)
	public List<BookingResponse> historyFor(Long customerId) {
		return bookingRepository.findByCustomerIdOrderByCreatedAtDesc(customerId).stream()
				.map(BookingResponse::from)
				.toList();
	}

	@Transactional(readOnly = true)
	public BookingResponse getOwned(Long customerId, Long bookingId) {
		return BookingResponse.from(requireOwned(customerId, bookingId));
	}

	@Transactional(readOnly = true)
	public BookingResponse getInternal(Long bookingId) {
		return BookingResponse.from(requireBooking(bookingId));
	}

	/**
	 * The date-range availability search, moved here from
	 * {@code CarService.search}.
	 *
	 * <p>Catalog supplies the candidates and reservation subtracts what it has
	 * booked - the same two steps as the monolith, just on the side of the
	 * boundary that owns the booking data. Doing it the other way round would
	 * have made catalog depend on reservation, which already depends on catalog.
	 */
	@Transactional(readOnly = true)
	public List<AvailableCarResponse> findAvailable(String location, String type,
			LocalDate startDate, LocalDate endDate) {
		List<InternalCarView> candidates = catalogClient.findRentable(location, type);

		if (startDate == null || endDate == null) {
			return candidates.stream().map(AvailableCarResponse::from).toList();
		}
		requireOrderedDates(startDate, endDate);

		Set<Long> booked = Set.copyOf(
				bookingRepository.findBookedCarIds(BookingStatus.BLOCKING, startDate, endDate));

		return candidates.stream()
				.filter(car -> !booked.contains(car.id()))
				.map(AvailableCarResponse::from)
				.toList();
	}

	@Transactional(readOnly = true)
	public ReservationStats stats() {
		Map<String, Long> byType = new LinkedHashMap<>();
		for (Object[] row : bookingRepository.countGroupedByCarType()) {
			byType.put((String) row[0], ((Number) row[1]).longValue());
		}
		return new ReservationStats(
				bookingRepository.count(),
				bookingRepository.countByStatus(BookingStatus.CONFIRMED),
				bookingRepository.countByStatus(BookingStatus.CANCELLED),
				byType);
	}

	// ------------------------------------------------------------------ helpers

	/**
	 * Claims one row per rented day.
	 *
	 * <p>This is the double-booking guarantee, and it is a write rather than a
	 * check on purpose. The monolith asked "is this car free?" and then inserted,
	 * with nothing between the two steps - so two concurrent requests could both
	 * be told yes. Here the database's primary key decides, and the loser gets a
	 * constraint violation.
	 *
	 * <p>{@code saveAllAndFlush} matters: without the flush the violation would
	 * surface at commit, outside this try block, and come back as a 500 instead of
	 * the 409 the monolith returned for the same situation.
	 *
	 * <p>Days are inserted in ascending order so that concurrent bookings on
	 * overlapping ranges always take row locks in the same sequence and cannot
	 * deadlock against each other.
	 */
	private void holdDays(Booking booking) {
		List<BookingDay> days = booking.getStartDate()
				.datesUntil(booking.getEndDate().plusDays(1))
				.sorted()
				.map(day -> new BookingDay(booking.getCarId(), day, booking.getId()))
				.toList();
		try {
			bookingDayRepository.saveAllAndFlush(days);
		}
		catch (DataIntegrityViolationException ex) {
			// Same message and status the monolith produced from its pre-check,
			// so the API contract is unchanged even though the mechanism is not.
			throw new ApiException(HttpStatus.CONFLICT,
					"This car is already booked for part of that date range");
		}
	}

	/** Inclusive of both ends, and never fewer than one day - as in the monolith. */
	private BigDecimal priceFor(BigDecimal dailyRate, LocalDate startDate, LocalDate endDate) {
		long days = Math.max(startDate.datesUntil(endDate.plusDays(1)).count(), 1);
		return dailyRate.multiply(BigDecimal.valueOf(days));
	}

	private void requireOrderedDates(LocalDate startDate, LocalDate endDate) {
		if (endDate.isBefore(startDate)) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "endDate cannot be before startDate");
		}
	}

	private Booking requireBooking(Long bookingId) {
		return bookingRepository.findById(bookingId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "No booking with id " + bookingId));
	}

	private Booking requireOwned(Long customerId, Long bookingId) {
		Booking booking = requireBooking(bookingId);
		if (!booking.isOwnedBy(customerId)) {
			throw new ApiException(HttpStatus.FORBIDDEN, "This booking does not belong to you");
		}
		return booking;
	}
}

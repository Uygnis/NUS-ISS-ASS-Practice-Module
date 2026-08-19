package org.rentez.accountservice.service;

import org.rentez.accountservice.client.StatsClient;
import org.rentez.accountservice.web.dto.ReportSummary;
import org.springframework.stereotype.Service;

/**
 * Composes the admin dashboard from the services that own the data.
 *
 * <p>The monolith's {@code ReportService} injected {@code CarRepository},
 * {@code BookingRepository} and {@code PaymentRepository} and did the whole
 * aggregation itself - three {@code findAll()} calls loading every row into
 * memory, then grouping bookings by {@code b.getCar().getType()}, dereferencing a
 * Car per booking.
 *
 * <p>None of that arithmetic happens here. Each service answers its own slice
 * with a local query - reservation can group by car type because the type is
 * snapshotted onto the booking row - and this class only stitches the three
 * answers together. The composer holds no domain logic, which is what keeps it
 * from becoming a distributed monolith wearing a dashboard.
 *
 * <p>Why it lives in account-service: nothing calls account-service on a request
 * path, because every service validates tokens locally. Read-only fan-out from a
 * service with no inbound dependencies cannot create a cycle. That is a property
 * to defend, not one to assume - the first
 * {@code GET /api/accounts/internal/users/{id}} anyone adds makes this a cycle,
 * and this is the code that would deadlock.
 */
@Service
public class ReportingService {

	private final StatsClient statsClient;

	public ReportingService(StatsClient statsClient) {
		this.statsClient = statsClient;
	}

	/**
	 * Never throws. Sections that did not answer come back null with
	 * {@code partial: true} - see {@link ReportSummary} for why a zero would be
	 * the wrong answer.
	 */
	public ReportSummary summary() {
		return ReportSummary.of(
				statsClient.catalogStats().orElse(null),
				statsClient.reservationStats().orElse(null),
				statsClient.paymentStats().orElse(null));
	}
}

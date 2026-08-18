package org.rentez.catalogservice.web.dto;

import org.rentez.catalogservice.domain.MaintenanceRecord;
import org.rentez.catalogservice.domain.MaintenanceStatus;

import java.time.LocalDate;

/**
 * A maintenance job, flattened to {@code carId} rather than nesting the car.
 *
 * <p>The association is still a real {@code @ManyToOne} in the entity - it is
 * intra-schema and legitimate - but it is LAZY, and serialising the entity
 * directly (as the monolith did) would force the proxy to initialise on every
 * response just to inline a car nobody asked for.
 */
public record MaintenanceResponse(
		Long id,
		Long carId,
		String description,
		LocalDate scheduledDate,
		LocalDate completedDate,
		MaintenanceStatus status) {

	public static MaintenanceResponse from(MaintenanceRecord record) {
		return new MaintenanceResponse(
				record.getId(),
				record.getCar().getId(),
				record.getDescription(),
				record.getScheduledDate(),
				record.getCompletedDate(),
				record.getStatus());
	}
}

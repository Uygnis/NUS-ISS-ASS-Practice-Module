package org.rentez.catalogservice.domain;

/** Progress of a maintenance job. Completing one returns the car to AVAILABLE. */
public enum MaintenanceStatus {
	SCHEDULED,
	IN_PROGRESS,
	COMPLETED
}

package org.rentez.accountservice.web.dto;

import org.rentez.accountservice.domain.AuditLog;

import java.time.Instant;

/**
 * One audit row, scoped to this service's own schema.
 *
 * <p>There is deliberately no cross-service audit feed. The monolith's
 * {@code GET /api/admin/audit-log} answered "the most recent N entries across
 * everything" from a single table; each service now owns its own trail, so the
 * equivalent view is per service. Routing all five to the shared
 * {@code rentez-audit} DynamoDB table was considered and rejected: its partition
 * key is {@code entityType#entityId}, so "latest N across all partitions" is a
 * full table Scan, and DynamoDB key schemas cannot be changed after creation.
 */
public record AuditLogResponse(
		Long id,
		String actorEmail,
		String action,
		String entityType,
		Long entityId,
		String details,
		Instant occurredAt) {

	public static AuditLogResponse from(AuditLog log) {
		return new AuditLogResponse(
				log.getId(),
				log.getActorEmail(),
				log.getAction(),
				log.getEntityType(),
				log.getEntityId(),
				log.getDetails(),
				log.getOccurredAt());
	}
}

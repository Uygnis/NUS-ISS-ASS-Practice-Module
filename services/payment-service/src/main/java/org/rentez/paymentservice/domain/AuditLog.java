package org.rentez.paymentservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Immutable trail of who did what, written by {@code AuditService} and only ever
 * read by admins.
 *
 * <p>This class ported across service boundaries without a single design change,
 * because it never held an object reference: the actor is an email string and
 * the target is an {@code (entityType, entityId)} pair. Every other entity in
 * the monolith used {@code @ManyToOne} for the same job and had to be rewritten.
 * Keep it that way - an {@code entityId} here may well name a row in another
 * service's schema, so it must stay an opaque number.
 */
@Entity
@Table(name = "audit_log")
public class AuditLog {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/** Nullable: some actions are taken by the system rather than a person. */
	@Column(name = "actor_email", length = 255)
	private String actorEmail;

	@Column(nullable = false, length = 64)
	private String action;

	@Column(name = "entity_type", nullable = false, length = 64)
	private String entityType;

	@Column(name = "entity_id")
	private Long entityId;

	@Column(length = 1000)
	private String details;

	@Column(name = "occurred_at", nullable = false)
	private Instant occurredAt = Instant.now();

	protected AuditLog() {
	}

	public AuditLog(String actorEmail, String action, String entityType, Long entityId, String details) {
		this.actorEmail = actorEmail;
		this.action = action;
		this.entityType = entityType;
		this.entityId = entityId;
		this.details = details;
	}

	public Long getId() { return id; }
	public String getActorEmail() { return actorEmail; }
	public String getAction() { return action; }
	public String getEntityType() { return entityType; }
	public Long getEntityId() { return entityId; }
	public String getDetails() { return details; }
	public Instant getOccurredAt() { return occurredAt; }
}

package com.CarRental_NUSISS.CarRental_NUSISS;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Immutable trail of who did what. Written by {@code AuditService}, never updated
 * or deleted through the API - only ever queried by admins.
 */
@Entity
@Table(name = "audit_log")
public class AuditLog {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/** Nullable because some actions are taken by the system itself (e.g. auto-cancel). */
	private String actorEmail;

	@Column(nullable = false)
	private String action;

	@Column(nullable = false)
	private String entityType;

	private Long entityId;

	@Column(length = 1000)
	private String details;

	@Column(nullable = false)
	private Instant timestamp = Instant.now();

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
	public Instant getTimestamp() { return timestamp; }
}

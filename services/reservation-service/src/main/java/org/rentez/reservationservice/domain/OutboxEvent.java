package org.rentez.reservationservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * A domain event waiting to be delivered to notification-service.
 *
 * <p>Written in the same transaction as the business change, which is the whole
 * point: the monolith called {@code notificationService.bookingCancelled(...)}
 * inline, so a notification failure could take down a cancellation, and a crash
 * between the two writes lost the notification with no record that it was owed.
 *
 * <p>{@code eventId} is generated here and carried through to the consumer, which
 * enforces a UNIQUE index on it. Delivery is therefore at-least-once and
 * de-duplicated at the far end - the property a broker would demand later, built
 * in now so adopting one is a transport change rather than a redesign.
 */
@Entity
@Table(name = "outbox_event")
public class OutboxEvent {

	public enum Status { PENDING, DISPATCHED, FAILED }

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "event_id", nullable = false, unique = true, length = 36)
	private String eventId;

	@Column(name = "event_type", nullable = false, length = 64)
	private String eventType;

	// The column is JSONB. Hibernate infers varchar for a bare String field and
	// ddl-auto=validate then rejects the mismatch, so the JDBC type is stated
	// explicitly. The field stays a String - nothing in this service reads into
	// the payload, it only carries it to the relay.
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false, columnDefinition = "jsonb")
	private String payload;

	@Column(nullable = false, length = 16)
	@jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
	private Status status = Status.PENDING;

	@Column(name = "attempt_count", nullable = false)
	private int attemptCount = 0;

	@Column(name = "last_error", length = 1000)
	private String lastError;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt = Instant.now();

	@Column(name = "dispatched_at")
	private Instant dispatchedAt;

	protected OutboxEvent() {
	}

	/**
	 * The id is passed in rather than generated here so the caller can put the
	 * same value into the payload it serialises. The relay then posts that payload
	 * byte for byte, with no rewriting in transit - an earlier version spliced the
	 * id into the JSON on the way out, which is string surgery on a structured
	 * document and a good way to ship an unparseable body.
	 */
	public OutboxEvent(String eventId, String eventType, String payload) {
		this.eventId = eventId;
		this.eventType = eventType;
		this.payload = payload;
	}

	/** Convenience for callers with no id of their own to propagate. */
	public static String newEventId() {
		return UUID.randomUUID().toString();
	}

	public void markDispatched() {
		this.status = Status.DISPATCHED;
		this.dispatchedAt = Instant.now();
		this.lastError = null;
	}

	public void markFailed(String error) {
		this.attemptCount++;
		this.lastError = error == null ? null : error.substring(0, Math.min(error.length(), 1000));
	}

	public Long getId() { return id; }
	public String getEventId() { return eventId; }
	public String getEventType() { return eventType; }
	public String getPayload() { return payload; }
	public Status getStatus() { return status; }
	public int getAttemptCount() { return attemptCount; }
	public String getLastError() { return lastError; }
	public Instant getCreatedAt() { return createdAt; }
	public Instant getDispatchedAt() { return dispatchedAt; }
}

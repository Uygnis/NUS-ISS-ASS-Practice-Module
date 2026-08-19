package org.rentez.notificationservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * A message sent to a user.
 *
 * <p>The monolith's version held {@code @ManyToOne User recipient} and was built
 * by a service that composed its own text:
 *
 * <pre>
 *   send(booking.getCustomer(), "BOOKING_CONFIRMED",
 *        "Your booking #%d for %s %s ...".formatted(
 *            booking.getId(), booking.getCar().getMake(), booking.getCar().getModel(), ...));
 * </pre>
 *
 * <p>One notification therefore read from the account, booking and fleet domains.
 * Now the producer renders the message from data it already owns and sends flat
 * values, so this service never dereferences anything and has zero outbound
 * dependencies.
 *
 * <p>{@code eventId} is supplied by the producer, not generated here - that is
 * what makes redelivery of the same event idempotent.
 */
@Entity
@Table(name = "notification")
public class Notification {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "event_id", nullable = false, unique = true, length = 36)
	private String eventId;

	@Column(name = "recipient_id", nullable = false)
	private Long recipientId;

	@Column(name = "recipient_email", nullable = false, length = 255)
	private String recipientEmail;

	@Column(nullable = false, length = 64)
	private String type;

	@Column(nullable = false, length = 1000)
	private String message;

	@Column(name = "related_entity_type", length = 32)
	private String relatedEntityType;

	@Column(name = "related_entity_id")
	private Long relatedEntityId;

	/**
	 * Mapped to is_read. The field cannot be called {@code read} at the column
	 * level: READ is reserved in MySQL 8 and the DDL fails.
	 */
	@Column(name = "is_read", nullable = false)
	private boolean read = false;

	@Column(name = "sent_at", nullable = false)
	private Instant sentAt = Instant.now();

	protected Notification() {
	}

	public Notification(String eventId, Long recipientId, String recipientEmail, String type, String message,
			String relatedEntityType, Long relatedEntityId) {
		this.eventId = eventId;
		this.recipientId = recipientId;
		this.recipientEmail = recipientEmail;
		this.type = type;
		this.message = message;
		this.relatedEntityType = relatedEntityType;
		this.relatedEntityId = relatedEntityId;
	}

	public boolean isOwnedBy(Long userId) {
		return recipientId.equals(userId);
	}

	public Long getId() { return id; }
	public String getEventId() { return eventId; }
	public Long getRecipientId() { return recipientId; }
	public String getRecipientEmail() { return recipientEmail; }
	public String getType() { return type; }
	public String getMessage() { return message; }
	public String getRelatedEntityType() { return relatedEntityType; }
	public Long getRelatedEntityId() { return relatedEntityId; }
	public boolean isRead() { return read; }
	public void setRead(boolean read) { this.read = read; }
	public Instant getSentAt() { return sentAt; }
}

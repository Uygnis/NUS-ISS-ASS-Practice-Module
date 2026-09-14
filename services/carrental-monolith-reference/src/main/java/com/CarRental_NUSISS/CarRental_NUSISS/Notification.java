package com.CarRental_NUSISS.CarRental_NUSISS;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * A message sent to a user. {@code NotificationService} writes these and, in this
 * prototype, also logs them to the console - swap in real email/SMS later without
 * touching callers.
 */
@Entity
@Table(name = "notification")
public class Notification {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(optional = false)
	@JoinColumn(name = "recipient_id")
	private User recipient;

	@Column(nullable = false)
	private String type;

	@Column(nullable = false, length = 1000)
	private String message;

	@Column(nullable = false)
	private boolean read = false;

	@Column(nullable = false)
	private Instant sentAt = Instant.now();

	protected Notification() {
	}

	public Notification(User recipient, String type, String message) {
		this.recipient = recipient;
		this.type = type;
		this.message = message;
	}

	public Long getId() { return id; }
	public User getRecipient() { return recipient; }
	public String getType() { return type; }
	public String getMessage() { return message; }
	public boolean isRead() { return read; }
	public void setRead(boolean read) { this.read = read; }
	public Instant getSentAt() { return sentAt; }
}

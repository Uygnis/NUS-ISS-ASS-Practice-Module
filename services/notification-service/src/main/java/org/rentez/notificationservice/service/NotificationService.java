package org.rentez.notificationservice.service;

import org.rentez.notificationservice.domain.Notification;
import org.rentez.notificationservice.error.ApiException;
import org.rentez.notificationservice.repository.NotificationRepository;
import org.rentez.notificationservice.web.dto.IngestResult;
import org.rentez.notificationservice.web.dto.NotificationEventRequest;
import org.rentez.notificationservice.web.dto.NotificationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Accepts notification events and exposes them back to their recipient.
 *
 * <p>"Sending" still means persisting a row and logging it, exactly as in the
 * monolith - {@link #deliver} is the single seam to swap for a real email, SMS or
 * push provider without touching any producer.
 */
@Service
public class NotificationService {

	private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

	private final NotificationRepository notificationRepository;

	public NotificationService(NotificationRepository notificationRepository) {
		this.notificationRepository = notificationRepository;
	}

	/**
	 * Idempotent ingest.
	 *
	 * <p>Deliberately <strong>not</strong> {@code @Transactional} at this level.
	 * The duplicate is detected by letting the unique index fire and catching the
	 * violation, and a failed insert inside a caller-owned transaction would mark
	 * that transaction rollback-only - so the subsequent lookup could not run.
	 * Leaving the boundary to {@code saveAndFlush} means the failed insert rolls
	 * back on its own and this method is still free to query.
	 *
	 * <p>The pre-check is an optimisation for the common case, not the guarantee.
	 * Two relays redelivering the same event concurrently will both pass it, both
	 * attempt the insert, and exactly one will lose - which is the case the catch
	 * block exists for. Checking alone would be the same time-of-check-to-time-of-use
	 * mistake the booking table was built to eliminate.
	 */
	public IngestResult ingest(NotificationEventRequest request) {
		return notificationRepository.findByEventId(request.eventId())
				.map(existing -> new IngestResult(existing.getId(), existing.getEventId(), true))
				.orElseGet(() -> insert(request));
	}

	private IngestResult insert(NotificationEventRequest request) {
		Notification notification = new Notification(
				request.eventId(), request.recipientId(), request.recipientEmail(),
				request.type(), request.message(), request.relatedEntityType(), request.relatedEntityId());
		try {
			Notification saved = notificationRepository.saveAndFlush(notification);
			deliver(saved);
			return new IngestResult(saved.getId(), saved.getEventId(), false);
		}
		catch (DataIntegrityViolationException ex) {
			// Lost the race against a concurrent redelivery of the same event.
			// The other writer has it; report a duplicate rather than an error.
			return notificationRepository.findByEventId(request.eventId())
					.map(existing -> new IngestResult(existing.getId(), existing.getEventId(), true))
					.orElseThrow(() -> new ApiException(HttpStatus.CONFLICT,
							"Could not store notification event " + request.eventId()));
		}
	}

	/**
	 * The mock delivery seam, carried over from the monolith unchanged: persisting
	 * the row is the record, and this is where a real provider goes.
	 */
	private void deliver(Notification notification) {
		log.info("[notification -> {}] ({}) {}",
				notification.getRecipientEmail(), notification.getType(), notification.getMessage());
	}

	@Transactional(readOnly = true)
	public List<NotificationResponse> forRecipient(Long recipientId) {
		return notificationRepository.findByRecipientIdOrderBySentAtDesc(recipientId).stream()
				.map(NotificationResponse::from)
				.toList();
	}

	@Transactional(readOnly = true)
	public long unreadCount(Long recipientId) {
		return notificationRepository.countByRecipientIdAndReadFalse(recipientId);
	}

	/** Marking read is scoped to the owner - a notification is addressed to one person. */
	@Transactional
	public NotificationResponse markRead(Long recipientId, Long notificationId) {
		Notification notification = notificationRepository.findById(notificationId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
						"No notification with id " + notificationId));

		if (!notification.isOwnedBy(recipientId)) {
			throw new ApiException(HttpStatus.FORBIDDEN, "This notification does not belong to you");
		}

		notification.setRead(true);
		return NotificationResponse.from(notificationRepository.save(notification));
	}
}

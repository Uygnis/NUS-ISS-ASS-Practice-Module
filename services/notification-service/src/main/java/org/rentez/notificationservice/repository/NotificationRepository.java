package org.rentez.notificationservice.repository;

import org.rentez.notificationservice.domain.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

	/**
	 * Carried over from the monolith, where it existed but was <em>never called</em>
	 * - no controller read notifications back, so the table was write-only. It
	 * finally has a caller.
	 */
	List<Notification> findByRecipientIdOrderBySentAtDesc(Long recipientId);

	long countByRecipientIdAndReadFalse(Long recipientId);

	/** The de-duplication lookup for a redelivered event. */
	Optional<Notification> findByEventId(String eventId);
}

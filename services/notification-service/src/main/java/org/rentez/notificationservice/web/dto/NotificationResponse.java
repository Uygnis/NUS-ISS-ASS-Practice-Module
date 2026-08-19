package org.rentez.notificationservice.web.dto;

import org.rentez.notificationservice.domain.Notification;

import java.time.Instant;

public record NotificationResponse(
		Long id,
		String type,
		String message,
		String relatedEntityType,
		Long relatedEntityId,
		boolean read,
		Instant sentAt) {

	public static NotificationResponse from(Notification notification) {
		return new NotificationResponse(
				notification.getId(),
				notification.getType(),
				notification.getMessage(),
				notification.getRelatedEntityType(),
				notification.getRelatedEntityId(),
				notification.isRead(),
				notification.getSentAt());
	}
}

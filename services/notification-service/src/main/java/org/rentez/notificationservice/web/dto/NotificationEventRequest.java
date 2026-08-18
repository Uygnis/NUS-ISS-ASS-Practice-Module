package org.rentez.notificationservice.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * One notification event, as posted by another service's outbox relay.
 *
 * <p>Every field is a flat value. There is deliberately no booking id to look up,
 * no user to fetch and no template to resolve against another service - the
 * producer rendered {@code message} from data it already owned. That is what lets
 * this service have zero outbound dependencies.
 *
 * <p>{@code eventId} comes from the producer and is the idempotency key.
 */
public record NotificationEventRequest(
		@NotBlank @Size(max = 36) String eventId,
		@NotNull Long recipientId,
		@NotBlank @Size(max = 255) String recipientEmail,
		@NotBlank @Size(max = 64) String type,
		@NotBlank @Size(max = 1000) String message,
		@Size(max = 32) String relatedEntityType,
		Long relatedEntityId) {
}

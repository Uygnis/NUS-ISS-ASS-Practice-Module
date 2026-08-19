package org.rentez.notificationservice.web.dto;

/**
 * The outcome of ingesting an event.
 *
 * <p>A redelivery is a success, not a conflict: the relay is doing exactly what
 * at-least-once delivery requires, and answering 409 would make it retry forever
 * or mark a perfectly delivered event as failed. {@code duplicate} reports what
 * actually happened so redelivery rates stay observable rather than invisible.
 */
public record IngestResult(Long notificationId, String eventId, boolean duplicate) {
}

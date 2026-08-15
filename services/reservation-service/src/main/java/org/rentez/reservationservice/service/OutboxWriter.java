package org.rentez.reservationservice.service;

import org.rentez.reservationservice.domain.Booking;
import org.rentez.reservationservice.domain.OutboxEvent;
import org.rentez.reservationservice.repository.OutboxEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Records notification events for later delivery.
 *
 * <p>Replaces the monolith's direct {@code notificationService.bookingCancelled(booking)}
 * calls. Two problems went away with them:
 *
 * <ul>
 *   <li>Notification was on the critical path, so an outage there could fail a
 *       cancellation.</li>
 *   <li>{@code NotificationService} took whole entities and walked
 *       {@code booking.getCustomer()} and {@code booking.getCar().getMake()} to
 *       build a message - a read into two other bounded contexts, per message.</li>
 * </ul>
 *
 * <p>The message is rendered <em>here</em>, by the service that owns the data,
 * and the event carries flat values. Notification never dereferences anything.
 *
 * <p>{@code MANDATORY} propagation is the safety catch: writing an outbox row
 * outside the caller's transaction would be just another non-atomic write, which
 * is exactly the failure the outbox exists to prevent. Calling this without a
 * transaction is a programming error and fails loudly rather than silently
 * degrading.
 */
@Service
public class OutboxWriter {

	private final OutboxEventRepository outboxRepository;
	private final ObjectMapper objectMapper;

	public OutboxWriter(OutboxEventRepository outboxRepository, ObjectMapper objectMapper) {
		this.outboxRepository = outboxRepository;
		this.objectMapper = objectMapper;
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public void bookingCancelled(Booking booking) {
		write("BOOKING_CANCELLED", booking,
				"Your booking #%d has been cancelled.".formatted(booking.getId()));
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public void bookingConfirmed(Booking booking) {
		write("BOOKING_CONFIRMED", booking,
				"Your booking #%d for %s %s from %s to %s is confirmed.".formatted(
						booking.getId(), booking.getCarMake(), booking.getCarModel(),
						booking.getStartDate(), booking.getEndDate()));
	}

	private void write(String eventType, Booking booking, String message) {
		ObjectNode payload = objectMapper.createObjectNode();
		payload.put("recipientId", booking.getCustomerId());
		payload.put("recipientEmail", booking.getCustomerEmail());
		payload.put("type", eventType);
		payload.put("message", message);
		payload.put("relatedEntityType", "BOOKING");
		payload.put("relatedEntityId", booking.getId());

		outboxRepository.save(new OutboxEvent(eventType, objectMapper.writeValueAsString(payload)));
	}
}

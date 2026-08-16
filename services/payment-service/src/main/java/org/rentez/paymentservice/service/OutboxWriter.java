package org.rentez.paymentservice.service;

import org.rentez.paymentservice.domain.OutboxEvent;
import org.rentez.paymentservice.domain.Payment;
import org.rentez.paymentservice.repository.OutboxEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Records notification events for later delivery.
 *
 * <p>Replaces {@code notificationService.paymentReceipt(payment)} and
 * {@code refundProcessed(payment)}, which the monolith called inline - and which
 * reached the recipient by walking
 * {@code payment.getBooking().getCustomer()}, two hops into two other domains.
 * The customer's address is snapshotted onto the payment, so the message is
 * rendered here from data this service owns.
 *
 * <p>{@code MANDATORY} propagation: an outbox row written outside the caller's
 * transaction is just another non-atomic write, which is the exact failure the
 * outbox exists to prevent.
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
	public void paymentReceipt(Payment payment) {
		write("PAYMENT_RECEIPT", payment,
				"Payment of %s %s received for booking #%d (ref %s).".formatted(
						payment.getCurrency(), payment.getAmount(),
						payment.getBookingId(), payment.getTransactionRef()));
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public void refundProcessed(Payment payment) {
		write("REFUND_PROCESSED", payment,
				"Refund of %s %s issued for booking #%d.".formatted(
						payment.getCurrency(), payment.getAmount(), payment.getBookingId()));
	}

	private void write(String eventType, Payment payment, String message) {
		String eventId = OutboxEvent.newEventId();

		ObjectNode payload = objectMapper.createObjectNode();
		payload.put("eventId", eventId);
		payload.put("recipientId", payment.getCustomerId());
		payload.put("recipientEmail", payment.getCustomerEmail());
		payload.put("type", eventType);
		payload.put("message", message);
		payload.put("relatedEntityType", "PAYMENT");
		payload.put("relatedEntityId", payment.getId());

		outboxRepository.save(new OutboxEvent(eventId, eventType, objectMapper.writeValueAsString(payload)));
	}
}

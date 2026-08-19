package org.rentez.paymentservice.client;

import org.rentez.paymentservice.security.ServiceTokenProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Delivers outbox events to notification-service.
 *
 * <p>Only ever called from the relay, never from a request thread. That is the
 * whole point of the outbox: in the monolith, booking and payment code called
 * {@code notificationService} inline, so notification sat on the critical path of
 * a cancellation.
 *
 * <p>The event body is already fully rendered - this client posts bytes and does
 * not know or care what a booking is.
 */
@Component
public class NotificationClient {

	private final RestClient restClient;
	private final ServiceTokenProvider tokens;

	public NotificationClient(RestClient.Builder builder,
			@Value("${rentez.notification.base-url}") String baseUrl,
			ServiceTokenProvider tokens) {
		this.restClient = builder.baseUrl(baseUrl).build();
		this.tokens = tokens;
	}

	/**
	 * Posts one event, exactly as stored.
	 *
	 * <p>The payload already contains its {@code eventId} - written there by
	 * {@code OutboxWriter} from the same variable that set the row's column - so
	 * this method does not rewrite the document on the way out. (MySQL's native
	 * JSON column does reformat it in storage, reordering keys and spacing them
	 * out, but the value is unchanged.) Throws on any non-2xx, which the relay
	 * treats as "leave it PENDING and try again"; the redelivery is harmless
	 * because the consumer de-duplicates on that id.
	 */
	public void send(String eventId, String payloadJson) {
		restClient.post()
				.uri("/api/notifications/internal/events")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.token())
				.contentType(MediaType.APPLICATION_JSON)
				.body(payloadJson)
				.retrieve()
				.toBodilessEntity();
	}
}

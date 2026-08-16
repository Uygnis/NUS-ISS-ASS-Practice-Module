package org.rentez.accountservice.client;

import org.rentez.accountservice.security.ServiceTokenProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Optional;

/**
 * Fetches the other services' report slices.
 *
 * <p>Everything about this client is shaped by one fact: it lives inside the
 * service that owns login. account-service is otherwise <em>inbound-dependency
 * free</em> - every service validates JWTs locally against the shared secret, so
 * nothing calls it on a request path - and that acyclicity is what makes it a
 * safe place to compose a report at all. But it means a slow catalog must never
 * be able to delay an authentication.
 *
 * <p>Hence: a dedicated {@link RestClient} with its own short timeouts rather
 * than the shared builder, and {@link #fetch} that returns empty instead of
 * throwing. An admin dashboard is worth degrading; it is not worth an outage.
 *
 * <p>There are deliberately no retries. This is a read of a number on a
 * dashboard - retrying multiplies load on a service that is already struggling,
 * which is how one slow service becomes three.
 */
@Component
public class StatsClient {

	private static final Logger log = LoggerFactory.getLogger(StatsClient.class);
	private static final Duration CONNECT_TIMEOUT = Duration.ofMillis(500);
	private static final Duration READ_TIMEOUT = Duration.ofSeconds(1);

	private final RestClient catalog;
	private final RestClient reservation;
	private final RestClient payment;
	private final ServiceTokenProvider tokens;

	public StatsClient(RestClient.Builder builder,
			@Value("${rentez.catalog.base-url}") String catalogUrl,
			@Value("${rentez.reservation.base-url}") String reservationUrl,
			@Value("${rentez.payment.base-url}") String paymentUrl,
			ServiceTokenProvider tokens) {

		this.catalog = reporting(builder, catalogUrl);
		this.reservation = reporting(builder, reservationUrl);
		this.payment = reporting(builder, paymentUrl);
		this.tokens = tokens;
	}

	/**
	 * A client with its own request factory, so these timeouts apply here and
	 * nowhere else - the point is that reporting cannot tie up threads that
	 * authentication needs.
	 */
	private static RestClient reporting(RestClient.Builder builder, String baseUrl) {
		ClientHttpRequestFactory factory = ClientHttpRequestFactoryBuilder.simple()
				.withCustomizer(simple -> {
					simple.setConnectTimeout(CONNECT_TIMEOUT);
					simple.setReadTimeout(READ_TIMEOUT);
				})
				.build();

		return builder.clone()
				.baseUrl(baseUrl)
				.requestFactory(factory)
				.build();
	}

	public Optional<CatalogStats> catalogStats() {
		return fetch("catalog", catalog, "/api/catalog/internal/stats", CatalogStats.class);
	}

	public Optional<ReservationStats> reservationStats() {
		return fetch("reservation", reservation, "/api/reservations/internal/stats", ReservationStats.class);
	}

	public Optional<PaymentStats> paymentStats() {
		return fetch("payment", payment, "/api/payments/internal/stats", PaymentStats.class);
	}

	/**
	 * Fail-soft: an unreachable service becomes a missing section, not an error.
	 *
	 * <p>Catching {@code Exception} is deliberate rather than lazy. The caller is
	 * assembling a dashboard from three independent sources and there is no
	 * failure from any of them that should be allowed to escape - a timeout, a
	 * 500, a malformed body and a DNS failure all mean the same thing here: that
	 * section is unavailable right now.
	 */
	private <T> Optional<T> fetch(String name, RestClient client, String uri, Class<T> type) {
		try {
			return Optional.ofNullable(client.get()
					.uri(uri)
					.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.token())
					.retrieve()
					.body(type));
		}
		catch (Exception ex) {
			log.warn("Report section '{}' unavailable: {}", name, ex.getMessage());
			return Optional.empty();
		}
	}
}

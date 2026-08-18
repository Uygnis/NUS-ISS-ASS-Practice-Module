package org.rentez.notificationservice;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.rentez.notificationservice.repository.NotificationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The de-duplication guarantee, and the recipient-facing API.
 *
 * <p>Idempotent ingest is what lets the producers' relays retry freely. If a
 * redelivery created a second row, every lost HTTP response in the system would
 * turn into a duplicate message to a customer.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class NotificationIngestTest {

	@Autowired
	private WebApplicationContext context;

	@Autowired
	private NotificationRepository notificationRepository;

	private MockMvc mvc() {
		return MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
	}

	private static String event(String eventId, long recipientId) {
		return """
				{"eventId":"%s","recipientId":%d,"recipientEmail":"user%d@example.com",
				 "type":"BOOKING_CANCELLED","message":"Your booking #1 has been cancelled.",
				 "relatedEntityType":"BOOKING","relatedEntityId":1}
				""".formatted(eventId, recipientId, recipientId);
	}

	@Test
	void redeliveringTheSameEventCreatesExactlyOneNotification() throws Exception {
		MockMvc mvc = mvc();
		String eventId = UUID.randomUUID().toString();

		String first = mvc.perform(post("/api/notifications/internal/events")
						.header("Authorization", "Bearer " + TestTokens.service())
						.contentType(MediaType.APPLICATION_JSON).content(event(eventId, 501L)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.duplicate").value(false))
				.andReturn().getResponse().getContentAsString();

		// The relay retries whenever a response is lost. This must succeed, not 409.
		String second = mvc.perform(post("/api/notifications/internal/events")
						.header("Authorization", "Bearer " + TestTokens.service())
						.contentType(MediaType.APPLICATION_JSON).content(event(eventId, 501L)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.duplicate").value(true))
				.andReturn().getResponse().getContentAsString();

		// Same row both times, and only one exists.
		assertThat(JsonPath.read(second, "$.notificationId").toString())
				.isEqualTo(JsonPath.read(first, "$.notificationId").toString());
		assertThat(notificationRepository.findByEventId(eventId)).isPresent();
		assertThat(notificationRepository.findByRecipientIdOrderBySentAtDesc(501L)).hasSize(1);
	}

	/**
	 * Two relays delivering the same event at once both pass the existence check,
	 * so the unique index is what actually decides. Without the catch in
	 * {@code NotificationService.insert}, one of these would surface as a 500.
	 */
	@Test
	void concurrentRedeliveryOfOneEventStillCreatesOneNotification() throws Exception {
		String eventId = UUID.randomUUID().toString();
		int threads = 6;

		CountDownLatch startLine = new CountDownLatch(1);
		AtomicInteger ok = new AtomicInteger();
		AtomicInteger failed = new AtomicInteger();

		ExecutorService pool = Executors.newFixedThreadPool(threads);
		try {
			List<Callable<Void>> attempts = java.util.stream.IntStream.range(0, threads)
					.mapToObj(i -> (Callable<Void>) () -> {
						MockMvc mvc = mvc();
						startLine.await();
						try {
							mvc.perform(post("/api/notifications/internal/events")
											.header("Authorization", "Bearer " + TestTokens.service())
											.contentType(MediaType.APPLICATION_JSON)
											.content(event(eventId, 502L)))
									.andExpect(status().isOk());
							ok.incrementAndGet();
						}
						catch (Throwable t) {
							failed.incrementAndGet();
						}
						return null;
					})
					.toList();

			List<Future<Void>> futures = attempts.stream().map(pool::submit).toList();
			startLine.countDown();
			for (Future<Void> future : futures) {
				future.get(60, TimeUnit.SECONDS);
			}
		}
		finally {
			pool.shutdownNow();
		}

		assertThat(failed.get()).as("every concurrent redelivery must be accepted").isZero();
		assertThat(ok.get()).isEqualTo(threads);
		assertThat(notificationRepository.findByRecipientIdOrderBySentAtDesc(502L))
				.as("the unique index must collapse them to one row")
				.hasSize(1);
	}

	@Test
	void ingestIsServiceOnly() throws Exception {
		MockMvc mvc = mvc();
		String body = event(UUID.randomUUID().toString(), 503L);

		mvc.perform(post("/api/notifications/internal/events")
						.header("Authorization", "Bearer " + TestTokens.customer())
						.contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isForbidden());

		mvc.perform(post("/api/notifications/internal/events")
						.contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void aRecipientSeesOnlyTheirOwnNotificationsNewestFirst() throws Exception {
		MockMvc mvc = mvc();
		long me = 601L;

		mvc.perform(post("/api/notifications/internal/events")
				.header("Authorization", "Bearer " + TestTokens.service())
				.contentType(MediaType.APPLICATION_JSON)
				.content(event(UUID.randomUUID().toString(), me))).andExpect(status().isOk());
		mvc.perform(post("/api/notifications/internal/events")
				.header("Authorization", "Bearer " + TestTokens.service())
				.contentType(MediaType.APPLICATION_JSON)
				.content(event(UUID.randomUUID().toString(), me))).andExpect(status().isOk());
		mvc.perform(post("/api/notifications/internal/events")
				.header("Authorization", "Bearer " + TestTokens.service())
				.contentType(MediaType.APPLICATION_JSON)
				.content(event(UUID.randomUUID().toString(), 602L))).andExpect(status().isOk());

		mvc.perform(get("/api/notifications/me").header("Authorization", "Bearer " + TestTokens.customer(me)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[0].type").value("BOOKING_CANCELLED"))
				// The recipient's address is not echoed back to them.
				.andExpect(jsonPath("$[0].recipientEmail").doesNotExist());

		mvc.perform(get("/api/notifications/me/unread-count")
						.header("Authorization", "Bearer " + TestTokens.customer(me)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.unread").value(2));
	}

	@Test
	void markingReadIsScopedToTheOwner() throws Exception {
		MockMvc mvc = mvc();
		long owner = 701L;

		String ingested = mvc.perform(post("/api/notifications/internal/events")
						.header("Authorization", "Bearer " + TestTokens.service())
						.contentType(MediaType.APPLICATION_JSON)
						.content(event(UUID.randomUUID().toString(), owner)))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();

		Number notificationId = JsonPath.read(ingested, "$.notificationId");

		mvc.perform(put("/api/notifications/" + notificationId + "/read")
						.header("Authorization", "Bearer " + TestTokens.customer(999L)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.message").value("This notification does not belong to you"));

		mvc.perform(put("/api/notifications/" + notificationId + "/read")
						.header("Authorization", "Bearer " + TestTokens.customer(owner)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.read").value(true));

		mvc.perform(get("/api/notifications/me/unread-count")
						.header("Authorization", "Bearer " + TestTokens.customer(owner)))
				.andExpect(jsonPath("$.unread").value(0));
	}

	@Test
	void rejectsAnEventMissingItsIdempotencyKey() throws Exception {
		mvc().perform(post("/api/notifications/internal/events")
						.header("Authorization", "Bearer " + TestTokens.service())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"recipientId":801,"recipientEmail":"a@b.com","type":"X","message":"hi"}
								"""))
				.andExpect(status().isBadRequest());
	}
}

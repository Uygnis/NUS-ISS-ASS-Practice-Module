package org.rentez.paymentservice.client;

import org.rentez.paymentservice.error.ApiException;
import org.rentez.paymentservice.security.ServiceTokenProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Payment's one outbound dependency.
 *
 * <p>Replaces three things the monolith did in-process against another
 * aggregate's table: reading the booking to learn what was owed, setting
 * {@code booking.setStatus(CONFIRMED)} after a successful charge, and setting
 * {@code CANCELLED} on refund. All three are now calls, and all three are
 * idempotent on the far side because this client retries them.
 */
@Component
public class ReservationClient {

	private final RestClient restClient;
	private final ServiceTokenProvider tokens;

	public ReservationClient(RestClient.Builder builder,
			@Value("${rentez.reservation.base-url}") String baseUrl,
			ServiceTokenProvider tokens) {
		this.restClient = builder.baseUrl(baseUrl).build();
		this.tokens = tokens;
	}

	/**
	 * The booking as reservation sees it - including the authoritative amount.
	 *
	 * <p>A transport failure here is the safest of all the failure modes: nothing
	 * has been charged, so failing fast with 503 leaves no inconsistency to clean
	 * up.
	 */
	public BookingView getBooking(Long bookingId) {
		try {
			BookingView booking = restClient.get()
					.uri("/api/reservations/internal/bookings/{id}", bookingId)
					.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.token())
					.retrieve()
					.onStatus(status -> status.value() == 404, (req, res) -> {
						throw new ApiException(HttpStatus.NOT_FOUND, "No booking with id " + bookingId);
					})
					.body(BookingView.class);

			if (booking == null) {
				throw new ApiException(HttpStatus.BAD_GATEWAY, "Reservation returned an empty response");
			}
			return booking;
		}
		catch (ApiException ex) {
			throw ex;
		}
		catch (RestClientException ex) {
			throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Reservation service is unavailable");
		}
	}

	/**
	 * Confirms a paid booking. Idempotent on the far side, so this is safe to
	 * retry after a lost response - which the sweeper does.
	 *
	 * @throws BookingUnconfirmableException when reservation refuses because the
	 *     booking is cancelled or completed. That is not a transport failure and
	 *     retrying will never help: the money has to go back instead.
	 */
	public void confirmBooking(Long bookingId) {
		try {
			restClient.post()
					.uri("/api/reservations/internal/bookings/{id}/confirm", bookingId)
					.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.token())
					.retrieve()
					.onStatus(status -> status.value() == 409, (req, res) -> {
						throw new BookingUnconfirmableException(
								"Booking " + bookingId + " can no longer be confirmed");
					})
					.toBodilessEntity();
		}
		catch (BookingUnconfirmableException ex) {
			throw ex;
		}
		catch (RestClientException ex) {
			throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Reservation service is unavailable");
		}
	}

	/** Compensation for a refunded payment. Idempotent on the far side. */
	public void cancelBooking(Long bookingId) {
		try {
			restClient.post()
					.uri("/api/reservations/internal/bookings/{id}/cancel", bookingId)
					.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.token())
					.retrieve()
					.toBodilessEntity();
		}
		catch (RestClientException ex) {
			throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Reservation service is unavailable");
		}
	}

	/**
	 * Reservation will not confirm this booking, and no amount of retrying will
	 * change that. Distinct from a transport failure precisely because the
	 * responses are opposite: one means try again, the other means give the money
	 * back.
	 */
	public static class BookingUnconfirmableException extends RuntimeException {

		public BookingUnconfirmableException(String message) {
			super(message);
		}
	}
}

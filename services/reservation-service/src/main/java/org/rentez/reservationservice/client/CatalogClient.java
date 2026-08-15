package org.rentez.reservationservice.client;

import org.rentez.reservationservice.error.ApiException;
import org.rentez.reservationservice.security.ServiceTokenProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;

/**
 * The only outbound dependency reservation has: catalog.
 *
 * <p>Replaces the monolith's {@code carRepository.findById(...)} inside
 * {@code BookingService.create}. Catalog remains the source of truth for whether
 * a car may be rented and what it costs; reservation asks once, at creation, and
 * then snapshots the answer so no later read has to ask again.
 *
 * <p>Calls go direct to the service, not through the gateway - the gateway is the
 * public edge and returns 404 for every {@code internal} path.
 */
@Component
public class CatalogClient {

	private final RestClient restClient;
	private final ServiceTokenProvider tokens;

	public CatalogClient(RestClient.Builder builder,
			@Value("${rentez.catalog.base-url}") String baseUrl,
			ServiceTokenProvider tokens) {
		this.restClient = builder.baseUrl(baseUrl).build();
		this.tokens = tokens;
	}

	/**
	 * The car as catalog sees it, or 404 if there is none.
	 *
	 * <p>A catalog outage surfaces as 503 rather than a misleading 404: the
	 * monolith could only fail this lookup by the car genuinely not existing, so
	 * mapping a transport failure onto "no such car" would invent a business
	 * error out of an infrastructure one.
	 */
	public InternalCarView getCar(Long carId) {
		try {
			InternalCarView car = restClient.get()
					.uri("/api/catalog/internal/cars/{id}", carId)
					.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.token())
					.retrieve()
					.onStatus(status -> status.value() == 404, (req, res) -> {
						throw new ApiException(HttpStatus.NOT_FOUND, "No car with id " + carId);
					})
					.body(InternalCarView.class);

			if (car == null) {
				throw new ApiException(HttpStatus.BAD_GATEWAY, "Catalog returned an empty response");
			}
			return car;
		}
		catch (ApiException ex) {
			throw ex;
		}
		catch (RestClientException ex) {
			throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Catalog service is unavailable");
		}
	}

	/** Rentable candidates for a location/type - the catalog half of an availability search. */
	public List<InternalCarView> findRentable(String location, String type) {
		try {
			InternalCarView[] cars = restClient.get()
					.uri(uriBuilder -> {
						uriBuilder.path("/api/catalog/internal/cars");
						if (location != null) {
							uriBuilder.queryParam("location", location);
						}
						if (type != null) {
							uriBuilder.queryParam("type", type);
						}
						return uriBuilder.build();
					})
					.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.token())
					.retrieve()
					.body(InternalCarView[].class);

			return cars == null ? List.of() : List.of(cars);
		}
		catch (RestClientException ex) {
			throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Catalog service is unavailable");
		}
	}
}

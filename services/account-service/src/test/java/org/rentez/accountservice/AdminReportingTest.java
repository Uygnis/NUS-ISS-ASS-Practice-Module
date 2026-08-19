package org.rentez.accountservice;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rentez.accountservice.client.CatalogStats;
import org.rentez.accountservice.client.PaymentStats;
import org.rentez.accountservice.client.ReservationStats;
import org.rentez.accountservice.client.StatsClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The admin report, composed from three services.
 *
 * <p>The monolith could not fail halfway - it read three repositories in one
 * transaction, so the summary was complete or the request errored. Composed
 * across services it can be partial, and the tests that matter are the ones
 * proving a missing section degrades honestly instead of either failing the whole
 * dashboard or, worse, reporting a confident zero.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class AdminReportingTest {

	@Autowired
	private WebApplicationContext context;

	@MockitoBean
	private StatsClient statsClient;

	private MockMvc mvc;

	@BeforeEach
	void setUp() {
		mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
	}

	private void allServicesAnswer() {
		given(statsClient.catalogStats()).willReturn(Optional.of(new CatalogStats(5, 3, 1)));
		given(statsClient.reservationStats()).willReturn(Optional.of(
				new ReservationStats(10, 6, 2, Map.of("SEDAN", 7L, "SUV", 3L))));
		given(statsClient.paymentStats()).willReturn(Optional.of(
				new PaymentStats(new BigDecimal("1234.50"), 6, 1, 1, 0)));
	}

	@Test
	void composesTheSameFiguresTheMonolithReported() throws Exception {
		allServicesAnswer();

		mvc.perform(get("/api/accounts/admin/reports/summary")
						.header("Authorization", "Bearer " + TestTokens.admin()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalCars").value(5))
				.andExpect(jsonPath("$.availableCars").value(3))
				.andExpect(jsonPath("$.carsInMaintenance").value(1))
				.andExpect(jsonPath("$.totalBookings").value(10))
				.andExpect(jsonPath("$.confirmedBookings").value(6))
				.andExpect(jsonPath("$.cancelledBookings").value(2))
				.andExpect(jsonPath("$.totalRevenue").value(1234.50))
				.andExpect(jsonPath("$.bookingsByCarType.SEDAN").value(7))
				.andExpect(jsonPath("$.partial").value(false))
				.andExpect(jsonPath("$.unavailableSections").isEmpty());
	}

	/**
	 * The important one. A missing section must be null and declared, never zero:
	 * "totalRevenue: 0" reads as a business fact, not as "payment-service is down",
	 * and an admin acting on that number would be acting on a lie.
	 */
	@Test
	void reportsAMissingSectionAsNullAndPartialRatherThanZero() throws Exception {
		allServicesAnswer();
		given(statsClient.paymentStats()).willReturn(Optional.empty());

		mvc.perform(get("/api/accounts/admin/reports/summary")
						.header("Authorization", "Bearer " + TestTokens.admin()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.partial").value(true))
				.andExpect(jsonPath("$.unavailableSections[0]").value("payment"))
				.andExpect(jsonPath("$.totalRevenue").doesNotExist())
				// The sections that did answer are still fully populated.
				.andExpect(jsonPath("$.totalCars").value(5))
				.andExpect(jsonPath("$.totalBookings").value(10));
	}

	/** Even with every downstream service gone, the dashboard endpoint still answers. */
	@Test
	void stillReturnsAnAnswerWhenEveryDownstreamServiceIsUnavailable() throws Exception {
		given(statsClient.catalogStats()).willReturn(Optional.empty());
		given(statsClient.reservationStats()).willReturn(Optional.empty());
		given(statsClient.paymentStats()).willReturn(Optional.empty());

		mvc.perform(get("/api/accounts/admin/reports/summary")
						.header("Authorization", "Bearer " + TestTokens.admin()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.partial").value(true))
				.andExpect(jsonPath("$.unavailableSections.length()").value(3))
				.andExpect(jsonPath("$.totalCars").doesNotExist())
				.andExpect(jsonPath("$.totalBookings").doesNotExist())
				.andExpect(jsonPath("$.totalRevenue").doesNotExist());
	}

	@Test
	void theReportIsAdminOnly() throws Exception {
		allServicesAnswer();

		mvc.perform(get("/api/accounts/admin/reports/summary")
						.header("Authorization", "Bearer " + TestTokens.customer()))
				.andExpect(status().isForbidden());

		mvc.perform(get("/api/accounts/admin/reports/summary"))
				.andExpect(status().isUnauthorized());
	}
}

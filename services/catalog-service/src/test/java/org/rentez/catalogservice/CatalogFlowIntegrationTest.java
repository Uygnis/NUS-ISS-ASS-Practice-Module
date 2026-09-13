package org.rentez.catalogservice;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Fleet management, the staff/admin split, and the internal contract reservation depends on. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class CatalogFlowIntegrationTest {

	@Autowired
	private WebApplicationContext context;

	private MockMvc mvc() {
		return MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
	}

	private String createCar(MockMvc mvc, String make, String model) throws Exception {
		String body = """
				{"make":"%s","model":"%s","year":2023,"dailyRate":80.00,"location":"Jurong","type":"SEDAN"}
				""".formatted(make, model);
		String created = mvc.perform(post("/api/catalog/cars")
						.header("Authorization", "Bearer " + TestTokens.admin())
						.contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return id(created);
	}

	/**
	 * Reads a numeric id out of a JSON body.
	 *
	 * <p>Assigned to a {@code Number} first on purpose: {@code JsonPath.read}
	 * returns a generic {@code T}, and passing it straight to
	 * {@code String.valueOf} makes javac select the {@code char[]} overload and
	 * infer {@code T = char[]}, which then fails at runtime with a ClassCastException.
	 */
	private static String id(String json) {
		Number value = JsonPath.read(json, "$.id");
		return value.toString();
	}

	/** Window-shopping needed no login in the monolith and still does not. */
	@Test
	void browsingIsPublic() throws Exception {
		mvc().perform(get("/api/catalog/cars")).andExpect(status().isOk());
	}

	@Test
	void adminCanManageTheFleetEndToEnd() throws Exception {
		MockMvc mvc = mvc();
		String id = createCar(mvc, "Toyota", "Corolla");

		mvc.perform(get("/api/catalog/cars/" + id))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.make").value("Toyota"))
				.andExpect(jsonPath("$.status").value("AVAILABLE"));

		mvc.perform(put("/api/catalog/cars/" + id)
						.header("Authorization", "Bearer " + TestTokens.admin())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"make":"Toyota","model":"Camry","year":2024,"dailyRate":95.00,"location":"Changi","type":"LUXURY"}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.model").value("Camry"))
				.andExpect(jsonPath("$.type").value("LUXURY"));
	}

	@Test
	void customersCannotChangeTheFleet() throws Exception {
		mvc().perform(post("/api/catalog/cars")
						.header("Authorization", "Bearer " + TestTokens.customer())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"make":"X","model":"Y","year":2020,"dailyRate":10.00,"location":"Z","type":"SEDAN"}
								"""))
				.andExpect(status().isForbidden());
	}

	/**
	 * Behaviour change from the monolith, asserted so it is not lost again. Both
	 * the old controller and service documented that STAFF may only set
	 * AVAILABLE or MAINTENANCE, and neither enforced it - so any staff account
	 * could RETIRE a car out of the fleet.
	 */
	@Test
	void staffMayTakeACarOutOfServiceButNotRetireIt() throws Exception {
		MockMvc mvc = mvc();
		String id = createCar(mvc, "Honda", "Civic");

		mvc.perform(patch("/api/catalog/cars/" + id + "/status")
						.header("Authorization", "Bearer " + TestTokens.staff())
						.param("status", "MAINTENANCE"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("MAINTENANCE"));

		mvc.perform(patch("/api/catalog/cars/" + id + "/status")
						.header("Authorization", "Bearer " + TestTokens.staff())
						.param("status", "RETIRED"))
				.andExpect(status().isForbidden());

		// The same call from an admin is allowed.
		mvc.perform(patch("/api/catalog/cars/" + id + "/status")
						.header("Authorization", "Bearer " + TestTokens.admin())
						.param("status", "RETIRED"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("RETIRED"));
	}

	/** Scheduling pulls the car from the rentable pool; completing puts it back. */
	@Test
	void maintenanceMovesTheCarOutOfAndBackIntoService() throws Exception {
		MockMvc mvc = mvc();
		String carId = createCar(mvc, "Mazda", "CX-5");

		String scheduled = mvc.perform(post("/api/catalog/maintenance")
						.header("Authorization", "Bearer " + TestTokens.admin())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"carId":%s,"description":"Brake pads","scheduledDate":"2026-09-01"}
								""".formatted(carId)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("SCHEDULED"))
				.andExpect(jsonPath("$.carId").value(Integer.parseInt(carId)))
				.andReturn().getResponse().getContentAsString();

		mvc.perform(get("/api/catalog/cars/" + carId))
				.andExpect(jsonPath("$.status").value("MAINTENANCE"));

		String recordId = id(scheduled);

		mvc.perform(put("/api/catalog/maintenance/" + recordId + "/status")
						.header("Authorization", "Bearer " + TestTokens.staff())
						.param("status", "COMPLETED"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.completedDate").isNotEmpty());

		mvc.perform(get("/api/catalog/cars/" + carId))
				.andExpect(jsonPath("$.status").value("AVAILABLE"));
	}

	/**
	 * The contract reservation-service depends on. If this shape changes,
	 * bookings break - so the field names are asserted explicitly.
	 */
	@Test
	void internalCarViewIsServiceOnlyAndExposesRentableNotStatus() throws Exception {
		MockMvc mvc = mvc();
		String id = createCar(mvc, "Tesla", "Model 3");

		mvc.perform(get("/api/catalog/internal/cars/" + id)
						.header("Authorization", "Bearer " + TestTokens.service()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.rentable").value(true))
				.andExpect(jsonPath("$.dailyRate").value(80.00))
				// A String, never the enum - so catalog can add a type without
				// breaking reservation's deserialisation.
				.andExpect(jsonPath("$.type").value("SEDAN"))
				.andExpect(jsonPath("$.status").doesNotExist());

		// An admin is not a service. Even the most privileged human token is
		// refused here, which is what keeps the internal surface internal.
		mvc.perform(get("/api/catalog/internal/cars/" + id)
						.header("Authorization", "Bearer " + TestTokens.admin()))
				.andExpect(status().isForbidden());

		mvc.perform(get("/api/catalog/internal/cars/" + id))
				.andExpect(status().isUnauthorized());
	}

	/**
	 * Reservation's availability search calls the internal rentable list with
	 * NEITHER filter set, on every request. That is the one path that reaches
	 * {@code CarRepository.findByFilters} with two null parameters, and on
	 * PostgreSQL it is a real trap: an untyped null binds as {@code bytea}, and
	 * the planner rejects the whole statement with "function lower(bytea) does
	 * not exist" before it ever evaluates the OR branch that would have skipped
	 * the filter. MySQL coerced silently, so this only surfaced during the port.
	 *
	 * <p>Every other test here passes at least one filter, or goes through
	 * {@code CarController}, which short-circuits the both-null case to a
	 * different query. This test exists so that the {@code cast(... as String)}
	 * cannot be tidied away by someone who reads it as noise.
	 */
	@Test
	void internalRentableListAcceptsNoFilters() throws Exception {
		MockMvc mvc = mvc();
		createCar(mvc, "Hyundai", "Avante");

		mvc.perform(get("/api/catalog/internal/cars")
						.header("Authorization", "Bearer " + TestTokens.service()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].rentable").value(true));

		// The filters still work when they are supplied.
		mvc.perform(get("/api/catalog/internal/cars")
						.param("location", "jurong")
						.header("Authorization", "Bearer " + TestTokens.service()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].rentable").value(true));

		mvc.perform(get("/api/catalog/internal/cars")
						.param("location", "nowhere")
						.header("Authorization", "Bearer " + TestTokens.service()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$").isEmpty());
	}

	@Test
	void internalCarViewReportsAMaintenanceCarAsNotRentable() throws Exception {
		MockMvc mvc = mvc();
		String id = createCar(mvc, "Toyota", "Hiace");

		mvc.perform(patch("/api/catalog/cars/" + id + "/status")
						.header("Authorization", "Bearer " + TestTokens.admin())
						.param("status", "MAINTENANCE"))
				.andExpect(status().isOk());

		mvc.perform(get("/api/catalog/internal/cars/" + id)
						.header("Authorization", "Bearer " + TestTokens.service()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.rentable").value(false));
	}

	@Test
	void unknownCarIsANotFound() throws Exception {
		mvc().perform(get("/api/catalog/cars/999999"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.message").value("No car with id 999999"));
	}
}

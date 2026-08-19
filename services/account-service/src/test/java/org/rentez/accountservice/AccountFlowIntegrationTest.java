package org.rentez.accountservice;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end check of the ported account domain against a real MySQL container:
 * register, log in, and read the profile back using the issued token.
 *
 * <p>The assertions that matter most are the negative ones. The monolith
 * serialised the {@code User} entity straight out of its controllers with a
 * public {@code getPasswordHash()} and no Jackson annotations anywhere, so every
 * BCrypt hash appeared in the body of {@code /api/users/me} and
 * {@code /api/admin/users}. These tests fail if that regresses.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class AccountFlowIntegrationTest {

	/** BCrypt hashes always carry one of these version prefixes. */
	private static final String[] BCRYPT_PREFIXES = { "$2a$", "$2b$", "$2y$" };

	@Autowired
	private WebApplicationContext context;

	private MockMvc mvc() {
		return MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
	}

	@Test
	void registersLogsInAndReadsOwnProfileWithoutEverLeakingThePasswordHash() throws Exception {
		MockMvc mvc = mvc();
		String email = "ada.leak.check@example.com";

		String registerBody = """
				{"fullName":"Ada Lovelace","email":"%s","password":"Sup3rSecret!","phone":"90000009"}
				""".formatted(email);

		String registered = mvc.perform(post("/api/accounts/auth/register")
						.contentType(MediaType.APPLICATION_JSON).content(registerBody))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.tokenType").value("Bearer"))
				.andExpect(jsonPath("$.role").value("CUSTOMER"))
				.andReturn().getResponse().getContentAsString();

		assertThat(registered).doesNotContain(BCRYPT_PREFIXES);

		String loginBody = """
				{"email":"%s","password":"Sup3rSecret!"}
				""".formatted(email);

		String loggedIn = mvc.perform(post("/api/accounts/auth/login")
						.contentType(MediaType.APPLICATION_JSON).content(loginBody))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();

		assertThat(loggedIn).doesNotContain(BCRYPT_PREFIXES);

		// The token has to actually authorize a protected endpoint - this is what
		// proves the NimbusJwtEncoder issuance and NimbusJwtDecoder validation
		// agree on the shared secret and algorithm.
		String token = JsonPath.read(loggedIn, "$.token");

		String profile = mvc.perform(get("/api/accounts/users/me").header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.email").value(email))
				.andExpect(jsonPath("$.fullName").value("Ada Lovelace"))
				.andReturn().getResponse().getContentAsString();

		assertThat(profile).doesNotContain(BCRYPT_PREFIXES);
		assertThat(profile).doesNotContain("passwordHash", "password");
	}

	@Test
	void rejectsAnUnauthenticatedProfileRequest() throws Exception {
		mvc().perform(get("/api/accounts/users/me")).andExpect(status().isUnauthorized());
	}

	/**
	 * A CUSTOMER token must not reach an admin endpoint. If
	 * {@code @EnableMethodSecurity} is ever dropped from SecurityConfig, the
	 * class-level {@code @PreAuthorize} on AdminUserController silently stops
	 * applying and this returns 200 instead of 403.
	 */
	@Test
	void refusesAdminEndpointsToACustomerToken() throws Exception {
		MockMvc mvc = mvc();
		String email = "not.an.admin@example.com";

		mvc.perform(post("/api/accounts/auth/register").contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"fullName":"Reg User","email":"%s","password":"Sup3rSecret!"}
								""".formatted(email)))
				.andExpect(status().isCreated());

		String loggedIn = mvc.perform(post("/api/accounts/auth/login").contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email":"%s","password":"Sup3rSecret!"}
								""".formatted(email)))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();

		String token = JsonPath.read(loggedIn, "$.token");

		mvc.perform(get("/api/accounts/admin/users").header("Authorization", "Bearer " + token))
				.andExpect(status().isForbidden());
	}

	@Test
	void rejectsAWrongPasswordWithoutRevealingWhetherTheAccountExists() throws Exception {
		MockMvc mvc = mvc();
		String email = "wrong.password@example.com";

		mvc.perform(post("/api/accounts/auth/register").contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"fullName":"Wrong Pw","email":"%s","password":"Sup3rSecret!"}
								""".formatted(email)))
				.andExpect(status().isCreated());

		mvc.perform(post("/api/accounts/auth/login").contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email":"%s","password":"NotThePassword!"}
								""".formatted(email)))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.message").value("Invalid email or password"));

		mvc.perform(post("/api/accounts/auth/login").contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email":"no.such.account@example.com","password":"NotThePassword!"}
								"""))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.message").value("Invalid email or password"));
	}

	@Test
	void rejectsADuplicateRegistration() throws Exception {
		MockMvc mvc = mvc();
		String body = """
				{"fullName":"Dupe","email":"dupe@example.com","password":"Sup3rSecret!"}
				""";

		mvc.perform(post("/api/accounts/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isCreated());
		mvc.perform(post("/api/accounts/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isConflict());
	}
}

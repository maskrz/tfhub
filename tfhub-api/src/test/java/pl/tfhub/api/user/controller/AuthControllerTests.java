package pl.tfhub.api.user.controller;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import pl.tfhub.api.user.service.UserSessionService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AuthControllerTests {

	private static final String USERNAME = "password-change-test";
	private static final String OTHER_USERNAME = "other-session-test";
	private static final String OLD_PASSWORD = "old-test-password";
	private static final String NEW_PASSWORD = "new-test-password";

	@Container
	private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

	@DynamicPropertySource
	static void configureDatabase(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		registry.add("spring.datasource.username", POSTGRES::getUsername);
		registry.add("spring.datasource.password", POSTGRES::getPassword);
	}

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private UserSessionService userSessionService;

	@BeforeEach
	void resetDatabase() {
		for (long userId : List.of(1L, 2L)) {
			userSessionService.getSessions(userId, null).forEach(
					session -> userSessionService.revoke(userId, session.id(), null));
		}
		jdbcTemplate.execute("TRUNCATE TABLE users RESTART IDENTITY CASCADE");
		jdbcTemplate.update(
				"INSERT INTO users (username, password_hash, role, password_changed_at) "
						+ "VALUES (?, ?, 'COACH', TIMESTAMPTZ '2000-01-01 00:00:00+00')",
				USERNAME, passwordEncoder.encode(OLD_PASSWORD));
	}

	@Test
	void listsAndRevokesOnlySessionsOwnedByTheAuthenticatedUser() throws Exception {
		MockHttpSession currentSession = authenticate(OLD_PASSWORD);
		MockHttpSession otherSession = authenticate(OLD_PASSWORD);

		JsonNode sessions = listSessions(currentSession);
		assertEquals(2, sessions.size());
		String otherHandle = null;
		boolean currentFound = false;
		for (JsonNode session : sessions) {
			String handle = session.get("id").asText();
			assertNotEquals(currentSession.getId(), handle);
			assertNotEquals(otherSession.getId(), handle);
			if (session.get("current").asBoolean()) {
				currentFound = true;
			} else {
				otherHandle = handle;
			}
		}
		assertTrue(currentFound);
		assertNotNull(otherHandle);

		CsrfToken csrf = csrfToken(currentSession);
		mockMvc.perform(delete("/api/auth/sessions/{sessionHandle}", otherHandle)
						.session(currentSession)
						.cookie(csrf.cookie())
						.header("X-XSRF-TOKEN", csrf.value()))
				.andExpect(status().isNoContent());

		assertTrue(otherSession.isInvalid());
		mockMvc.perform(get("/api/auth/session").session(otherSession))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/auth/session").session(currentSession))
				.andExpect(status().isOk());
	}

	@Test
	void revokingTheCurrentSessionEndsTheCurrentAuthentication() throws Exception {
		MockHttpSession session = authenticate(OLD_PASSWORD);
		JsonNode sessions = listSessions(session);
		String currentHandle = sessions.get(0).get("id").asText();
		CsrfToken csrf = csrfToken(session);

		mockMvc.perform(delete("/api/auth/sessions/{sessionHandle}", currentHandle)
						.session(session)
						.cookie(csrf.cookie())
						.header("X-XSRF-TOKEN", csrf.value()))
				.andExpect(status().isNoContent());

		assertTrue(session.isInvalid());
		mockMvc.perform(get("/api/auth/session").session(session))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void cannotRevokeAnotherUsersSession() throws Exception {
		jdbcTemplate.update(
				"INSERT INTO users (username, password_hash, role, password_changed_at) "
						+ "VALUES (?, ?, 'COACH', CURRENT_TIMESTAMP)",
				OTHER_USERNAME, passwordEncoder.encode(OLD_PASSWORD));
		MockHttpSession ownerSession = authenticate(OLD_PASSWORD);
		MockHttpSession otherUserSession = authenticate(OTHER_USERNAME, OLD_PASSWORD);
		String ownerHandle = listSessions(ownerSession).get(0).get("id").asText();
		CsrfToken csrf = csrfToken(otherUserSession);

		mockMvc.perform(delete("/api/auth/sessions/{sessionHandle}", ownerHandle)
						.session(otherUserSession)
						.cookie(csrf.cookie())
						.header("X-XSRF-TOKEN", csrf.value()))
				.andExpect(status().isNotFound());

		mockMvc.perform(get("/api/auth/session").session(ownerSession))
				.andExpect(status().isOk());
		mockMvc.perform(get("/api/auth/session").session(otherUserSession))
				.andExpect(status().isOk());
	}

	@Test
	void requiresCsrfForSessionRevocation() throws Exception {
		MockHttpSession session = authenticate(OLD_PASSWORD);
		String handle = listSessions(session).get(0).get("id").asText();

		mockMvc.perform(delete("/api/auth/sessions/{sessionHandle}", handle).session(session))
				.andExpect(status().isForbidden());

		mockMvc.perform(get("/api/auth/session").session(session))
				.andExpect(status().isOk());
	}

	@Test
	void changesPasswordAndInvalidatesOnlyTheCurrentSession() throws Exception {
		MockHttpSession currentSession = authenticate(OLD_PASSWORD);
		MockHttpSession otherSession = authenticate(OLD_PASSWORD);
		String oldHash = storedPasswordHash();

		CsrfToken csrf = csrfToken(currentSession);
		mockMvc.perform(post("/api/auth/change-password")
						.session(currentSession)
						.cookie(csrf.cookie())
						.header("X-XSRF-TOKEN", csrf.value())
						.contentType("application/json")
						.content("""
								{"currentPassword":"%s","newPassword":"%s"}
								""".formatted(OLD_PASSWORD, NEW_PASSWORD)))
				.andExpect(status().isNoContent())
				.andExpect(content().string(""));

		String newHash = storedPasswordHash();
		assertNotEquals(oldHash, newHash);
		assertTrue(newHash.startsWith("$argon2id$"));
		assertTrue(passwordEncoder.matches(NEW_PASSWORD, newHash));
		assertFalse(passwordEncoder.matches(OLD_PASSWORD, newHash));
		assertTrue(currentSession.isInvalid());
		assertTrue(jdbcTemplate.queryForObject(
				"SELECT password_changed_at > TIMESTAMPTZ '2000-01-01 00:00:00+00' FROM users",
				Boolean.class));

		mockMvc.perform(get("/api/auth/session").session(otherSession))
				.andExpect(status().isOk());

		authenticate(NEW_PASSWORD);
		assertEquals(401, authenticateResult(OLD_PASSWORD).getResponse().getStatus());
	}

	@Test
	void incorrectCurrentPasswordLeavesStoredPasswordUnchanged() throws Exception {
		MockHttpSession session = authenticate(OLD_PASSWORD);
		String oldHash = storedPasswordHash();
		CsrfToken csrf = csrfToken(session);

		mockMvc.perform(post("/api/auth/change-password")
						.session(session)
						.cookie(csrf.cookie())
						.header("X-XSRF-TOKEN", csrf.value())
						.contentType("application/json")
						.content("""
								{"currentPassword":"wrong-password","newPassword":"%s"}
								""".formatted(NEW_PASSWORD)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.title").value("Incorrect current password"));

		assertEquals(oldHash, storedPasswordHash());
	}

	@Test
	void unauthenticatedRequestsRequireCsrfAndThenReturnUnauthorized() throws Exception {
		CsrfToken csrf = csrfToken(null);
		String requestBody = """
				{"currentPassword":"%s","newPassword":"%s"}
				""".formatted(OLD_PASSWORD, NEW_PASSWORD);

		mockMvc.perform(post("/api/auth/change-password")
						.contentType("application/json")
						.content(requestBody))
				.andExpect(status().isForbidden());

		mockMvc.perform(post("/api/auth/change-password")
						.cookie(csrf.cookie())
						.header("X-XSRF-TOKEN", csrf.value())
						.contentType("application/json")
						.content(requestBody))
				.andExpect(status().isUnauthorized());

		assertTrue(passwordEncoder.matches(OLD_PASSWORD, storedPasswordHash()));
	}

	@Test
	void rejectsMissingNullBlankAndOversizedPasswordFields() throws Exception {
		MockHttpSession session = authenticate(OLD_PASSWORD);
		String oldHash = storedPasswordHash();
		CsrfToken csrf = csrfToken(session);

		assertInvalidRequest(session, csrf, """
				{"newPassword":"%s"}
				""".formatted(NEW_PASSWORD));
		assertInvalidRequest(session, csrf, """
				{"currentPassword":"%s"}
				""".formatted(OLD_PASSWORD));
		assertInvalidRequest(session, csrf, """
				{"currentPassword":null,"newPassword":"%s"}
				""".formatted(NEW_PASSWORD));
		assertInvalidRequest(session, csrf, """
				{"currentPassword":"%s","newPassword":null}
				""".formatted(OLD_PASSWORD));
		assertInvalidRequest(session, csrf, """
				{"currentPassword":" ","newPassword":"%s"}
				""".formatted(NEW_PASSWORD));
		assertInvalidRequest(session, csrf, """
				{"currentPassword":"%s","newPassword":" "}
				""".formatted(OLD_PASSWORD));
		assertInvalidRequest(session, csrf, """
				{"currentPassword":"%s","newPassword":"%s"}
				""".formatted(OLD_PASSWORD, "x".repeat(1025)));

		assertEquals(oldHash, storedPasswordHash());
	}

	private void assertInvalidRequest(MockHttpSession session, CsrfToken csrf, String body) throws Exception {
		mockMvc.perform(post("/api/auth/change-password")
						.session(session)
						.cookie(csrf.cookie())
						.header("X-XSRF-TOKEN", csrf.value())
						.contentType("application/json")
						.content(body))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.title").value("Invalid request"));
	}

	private MockHttpSession authenticate(String password) throws Exception {
		return authenticate(USERNAME, password);
	}

	private MockHttpSession authenticate(String username, String password) throws Exception {
		MvcResult result = authenticateResult(username, password);
		assertEquals(200, result.getResponse().getStatus());
		return (MockHttpSession) result.getRequest().getSession(false);
	}

	private MvcResult authenticateResult(String password) throws Exception {
		return authenticateResult(USERNAME, password);
	}

	private MvcResult authenticateResult(String username, String password) throws Exception {
		CsrfToken csrf = csrfToken(null);
		return mockMvc.perform(post("/api/auth/login")
						.cookie(csrf.cookie())
						.header("X-XSRF-TOKEN", csrf.value())
						.contentType("application/json")
						.content("""
								{"username":"%s","password":"%s"}
								""".formatted(username, password)))
				.andReturn();
	}

	private JsonNode listSessions(MockHttpSession session) throws Exception {
		MvcResult result = mockMvc.perform(get("/api/auth/sessions").session(session))
				.andExpect(status().isOk())
				.andReturn();
		return objectMapper.readTree(result.getResponse().getContentAsString());
	}

	private CsrfToken csrfToken(MockHttpSession session) throws Exception {
		var request = get("/api/csrf");
		if (session != null) {
			request.session(session);
		}
		MvcResult result = mockMvc.perform(request).andExpect(status().isOk()).andReturn();
		Cookie cookie = result.getResponse().getCookie("XSRF-TOKEN");
		return new CsrfToken(cookie, cookie.getValue());
	}

	private String storedPasswordHash() {
		return jdbcTemplate.queryForObject("SELECT password_hash FROM users WHERE username = ?", String.class, USERNAME);
	}

	private record CsrfToken(Cookie cookie, String value) {
	}
}

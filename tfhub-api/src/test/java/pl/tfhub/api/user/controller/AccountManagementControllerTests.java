package pl.tfhub.api.user.controller;

import jakarta.servlet.http.Cookie;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
import pl.tfhub.api.user.exception.LastActiveAdministratorException;
import pl.tfhub.api.user.service.AccountManagementService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AccountManagementControllerTests {

	private static final String ADMIN_PASSWORD = "admin-password";
	private static final String COACH_PASSWORD = "coach-password";
	private static final String RESET_PASSWORD = "reset-password";

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
	private AccountManagementService accountManagementService;

	@BeforeEach
	void resetDatabase() {
		jdbcTemplate.execute("TRUNCATE TABLE users, athletes, training_groups RESTART IDENTITY CASCADE");
		jdbcTemplate.update("INSERT INTO training_groups (name) VALUES ('Group A'), ('Group B')");
		jdbcTemplate.update("""
				INSERT INTO athletes (first_name, last_name, birth_year, gender)
				VALUES ('Alex', 'Runner', 2000, 'MALE')
				""");
		insertUser("admin", ADMIN_PASSWORD, "ADMIN");
		insertUser("coach", COACH_PASSWORD, "COACH");
		jdbcTemplate.update(
				"INSERT INTO user_training_group (user_id, training_group_id) VALUES (2, 1)");
	}

	@Test
	void adminCanListAndFilterAccountsWithoutExposingPasswordHashes() throws Exception {
		MockHttpSession admin = authenticate("admin", ADMIN_PASSWORD);

		String allAccounts = mockMvc.perform(get("/api/accounts").session(admin))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].username").value("admin"))
				.andExpect(jsonPath("$[0].role").value("ADMIN"))
				.andExpect(jsonPath("$[0].active").value(true))
				.andExpect(jsonPath("$[1].trainingGroups[0].name").value("Group A"))
				.andReturn().getResponse().getContentAsString();
		assertFalse(allAccounts.contains("password_hash"));
		assertFalse(allAccounts.contains("$argon2id$"));

		mockMvc.perform(get("/api/accounts")
						.param("username", "COA")
						.param("role", "COACH")
						.param("active", "true")
						.session(admin))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].username").value("coach"));

		MockHttpSession coach = authenticate("coach", COACH_PASSWORD);
		mockMvc.perform(get("/api/accounts").session(coach)).andExpect(status().isForbidden());
		mockMvc.perform(get("/api/accounts")).andExpect(status().isUnauthorized());
	}

	@Test
	void createsAllRolesAndRejectsInvalidOrConflictingAssignments() throws Exception {
		MockHttpSession admin = authenticate("admin", ADMIN_PASSWORD);
		Csrf csrf = csrfToken(admin);

		postAccount(admin, csrf, """
				{"username":"second-admin","password":"initial-password","role":"ADMIN"}
				""").andExpect(status().isCreated())
				.andExpect(jsonPath("$.trainingGroups").isEmpty())
				.andExpect(jsonPath("$.athlete").doesNotExist());

		postAccount(admin, csrf, """
				{"username":"multi-coach","password":"initial-password","role":"COACH","trainingGroupIds":[1,2]}
				""").andExpect(status().isCreated())
				.andExpect(jsonPath("$.trainingGroups.length()").value(2));

		postAccount(admin, csrf, """
				{"username":"athlete-user","password":"initial-password","role":"ATHLETE","athleteId":1}
				""").andExpect(status().isCreated())
				.andExpect(jsonPath("$.athlete.id").value(1))
				.andExpect(jsonPath("$.athlete.trainingGroupId").isEmpty());

		postAccount(admin, csrf, """
				{"username":"coach-without-groups","password":"initial-password","role":"COACH"}
				""").andExpect(status().isBadRequest());
		postAccount(admin, csrf, """
				{"username":"admin-with-group","password":"initial-password","role":"ADMIN","trainingGroupIds":[1]}
				""").andExpect(status().isBadRequest());
		postAccount(admin, csrf, """
				{"username":"unknown-group","password":"initial-password","role":"COACH","trainingGroupIds":[99]}
				""").andExpect(status().isNotFound());
		postAccount(admin, csrf, """
				{"username":"duplicate-athlete","password":"initial-password","role":"ATHLETE","athleteId":1}
				""").andExpect(status().isConflict());
		postAccount(admin, csrf, """
				{"username":" ADMIN ","password":"initial-password","role":"ADMIN"}
				""").andExpect(status().isConflict());
	}

	@Test
	void updatesUsernameRoleAndAssignmentsAndCleansObsoleteRelationships() throws Exception {
		MockHttpSession admin = authenticate("admin", ADMIN_PASSWORD);
		Csrf csrf = csrfToken(admin);

		mockMvc.perform(put("/api/accounts/2")
						.session(admin)
						.cookie(csrf.cookie())
						.header("X-XSRF-TOKEN", csrf.value())
						.contentType("application/json")
						.content("""
								{"username":"updated-athlete","role":"ATHLETE","athleteId":1}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.username").value("updated-athlete"))
				.andExpect(jsonPath("$.role").value("ATHLETE"))
				.andExpect(jsonPath("$.trainingGroups").isEmpty())
				.andExpect(jsonPath("$.athlete.id").value(1));
		assertEquals(0, count("SELECT COUNT(*) FROM user_training_group WHERE user_id = 2"));
		assertEquals(1, count("SELECT COUNT(*) FROM user_athlete_access WHERE user_id = 2 AND athlete_id = 1"));

		Csrf nextCsrf = csrfToken(admin);
		mockMvc.perform(put("/api/accounts/2")
						.session(admin)
						.cookie(nextCsrf.cookie())
						.header("X-XSRF-TOKEN", nextCsrf.value())
						.contentType("application/json")
						.content("""
								{"username":"updated-admin","role":"ADMIN"}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.role").value("ADMIN"))
				.andExpect(jsonPath("$.athlete").doesNotExist());
		assertEquals(0, count("SELECT COUNT(*) FROM user_athlete_access WHERE user_id = 2"));
	}

	@Test
	void activatesAndDeactivatesAccountsAndInactiveUsersCannotAuthenticate() throws Exception {
		MockHttpSession admin = authenticate("admin", ADMIN_PASSWORD);
		Csrf csrf = csrfToken(admin);

		mockMvc.perform(patch("/api/accounts/2/status")
						.session(admin)
						.cookie(csrf.cookie())
						.header("X-XSRF-TOKEN", csrf.value())
						.contentType("application/json")
						.content("{\"active\":false}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.active").value(false));
		mockMvc.perform(get("/api/accounts").param("active", "false").session(admin))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].username").value("coach"));
		assertEquals(401, authenticateResult("coach", COACH_PASSWORD).getResponse().getStatus());

		Csrf nextCsrf = csrfToken(admin);
		mockMvc.perform(patch("/api/accounts/2/status")
						.session(admin)
						.cookie(nextCsrf.cookie())
						.header("X-XSRF-TOKEN", nextCsrf.value())
						.contentType("application/json")
						.content("{\"active\":true}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.active").value(true));
		assertEquals(200, authenticateResult("coach", COACH_PASSWORD).getResponse().getStatus());
	}

	@Test
	void administratorCanResetPasswordUsingArgon2idAndOldSessionsAreRejected() throws Exception {
		MockHttpSession admin = authenticate("admin", ADMIN_PASSWORD);
		MockHttpSession oldCoachSession = authenticate("coach", COACH_PASSWORD);
		Csrf csrf = csrfToken(admin);

		mockMvc.perform(post("/api/accounts/2/password")
						.session(admin)
						.cookie(csrf.cookie())
						.header("X-XSRF-TOKEN", csrf.value())
						.contentType("application/json")
						.content("{\"newPassword\":\"" + RESET_PASSWORD + "\"}"))
				.andExpect(status().isNoContent());

		String hash = jdbcTemplate.queryForObject(
				"SELECT password_hash FROM users WHERE id = 2", String.class);
		assertTrue(hash.startsWith("$argon2id$"));
		assertTrue(passwordEncoder.matches(RESET_PASSWORD, hash));
		assertEquals(200, mockMvc.perform(get("/api/auth/session").session(oldCoachSession))
				.andReturn().getResponse().getStatus());
		assertEquals(200, authenticateResult("coach", RESET_PASSWORD).getResponse().getStatus());
		assertEquals(401, authenticateResult("coach", COACH_PASSWORD).getResponse().getStatus());

		MockHttpSession staleRoleSession = authenticate("coach", RESET_PASSWORD);
		Csrf nextCsrf = csrfToken(admin);
		mockMvc.perform(put("/api/accounts/2")
						.session(admin)
						.cookie(nextCsrf.cookie())
						.header("X-XSRF-TOKEN", nextCsrf.value())
						.contentType("application/json")
						.content("""
								{"username":"coach","role":"ATHLETE","athleteId":1}
								"""))
				.andExpect(status().isOk());
		assertEquals(401, mockMvc.perform(get("/api/auth/session").session(staleRoleSession))
				.andReturn().getResponse().getStatus());
	}

	@Test
	void protectsLastActiveAdministratorAndRequiresCsrfForMutations() throws Exception {
		MockHttpSession admin = authenticate("admin", ADMIN_PASSWORD);

		mockMvc.perform(post("/api/accounts")
						.session(admin)
						.contentType("application/json")
						.content("""
								{"username":"csrf-check","password":"initial-password","role":"ADMIN"}
								"""))
				.andExpect(status().isForbidden());

		Csrf csrf = csrfToken(admin);
		mockMvc.perform(patch("/api/accounts/1/status")
						.session(admin)
						.cookie(csrf.cookie())
						.header("X-XSRF-TOKEN", csrf.value())
						.contentType("application/json")
						.content("{\"active\":false}"))
				.andExpect(status().isConflict());
		assertTrue(jdbcTemplate.queryForObject(
				"SELECT active FROM users WHERE id = 1", Boolean.class));

		Csrf nextCsrf = csrfToken(admin);
		mockMvc.perform(put("/api/accounts/1")
						.session(admin)
						.cookie(nextCsrf.cookie())
						.header("X-XSRF-TOKEN", nextCsrf.value())
						.contentType("application/json")
						.content("""
								{"username":"admin","role":"COACH","trainingGroupIds":[1]}
								"""))
				.andExpect(status().isConflict());
		assertEquals("ADMIN", jdbcTemplate.queryForObject(
				"SELECT role FROM users WHERE id = 1", String.class));
	}

	@Test
	void concurrentDeactivationCannotRemoveAllActiveAdministrators() throws Exception {
		insertUser("second-admin", "second-admin-password", "ADMIN");
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Future<Boolean> first = executor.submit(() -> tryDeactivate(1, ready, start));
			Future<Boolean> second = executor.submit(() -> tryDeactivate(3, ready, start));
			ready.await();
			start.countDown();
			assertTrue(first.get() ^ second.get());
			assertEquals(1, count("SELECT COUNT(*) FROM users WHERE role = 'ADMIN' AND active = TRUE"));
		} finally {
			executor.shutdownNow();
		}
	}

	private boolean tryDeactivate(long id, CountDownLatch ready, CountDownLatch start) throws InterruptedException {
		ready.countDown();
		start.await();
		try {
			accountManagementService.changeStatus(id, false);
			return true;
		} catch (LastActiveAdministratorException exception) {
			return false;
		}
	}

	private org.springframework.test.web.servlet.ResultActions postAccount(
			MockHttpSession session, Csrf csrf, String body) throws Exception {
		return mockMvc.perform(post("/api/accounts")
				.session(session)
				.cookie(csrf.cookie())
				.header("X-XSRF-TOKEN", csrf.value())
				.contentType("application/json")
				.content(body));
	}

	private MockHttpSession authenticate(String username, String password) throws Exception {
		MvcResult result = authenticateResult(username, password);
		assertEquals(200, result.getResponse().getStatus());
		return (MockHttpSession) result.getRequest().getSession(false);
	}

	private MvcResult authenticateResult(String username, String password) throws Exception {
		Csrf csrf = csrfToken(null);
		return mockMvc.perform(post("/api/auth/login")
						.cookie(csrf.cookie())
						.header("X-XSRF-TOKEN", csrf.value())
						.contentType("application/json")
						.content("""
								{"username":"%s","password":"%s"}
								""".formatted(username, password)))
				.andReturn();
	}

	private Csrf csrfToken(MockHttpSession session) throws Exception {
		var request = get("/api/csrf");
		if (session != null) {
			request.session(session);
		}
		MvcResult result = mockMvc.perform(request).andExpect(status().isOk()).andReturn();
		Cookie cookie = result.getResponse().getCookie("XSRF-TOKEN");
		return new Csrf(cookie, cookie.getValue());
	}

	private void insertUser(String username, String password, String role) {
		jdbcTemplate.update(
				"INSERT INTO users (username, password_hash, role) VALUES (?, ?, ?)",
				username, passwordEncoder.encode(password), role);
	}

	private int count(String sql) {
		return jdbcTemplate.queryForObject(sql, Integer.class);
	}

	private record Csrf(Cookie cookie, String value) {
	}
}

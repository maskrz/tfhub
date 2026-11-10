package pl.tfhub.api.backup;

import jakarta.servlet.http.Cookie;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class BackupControllerSecurityTests {

	private static final String PASSWORD = "backup-test-password";

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

	@MockitoBean
	private BackupService backupService;

	@BeforeEach
	void resetUsers() {
		jdbcTemplate.execute(
				"TRUNCATE TABLE test_results, athletes, training_groups RESTART IDENTITY CASCADE");
		jdbcTemplate.execute("TRUNCATE TABLE users RESTART IDENTITY CASCADE");
		insertUser("admin", "ADMIN");
		insertUser("coach", "COACH");
		insertUser("athlete", "ATHLETE");
		Long athleteId = jdbcTemplate.queryForObject("""
				INSERT INTO athletes (first_name, last_name, birth_year, gender)
				VALUES ('Backup', 'Athlete', 2000, 'MALE')
				RETURNING id
				""", Long.class);
		jdbcTemplate.update(
				"INSERT INTO user_athlete_access (user_id, athlete_id) VALUES (3, ?)", athleteId);
		when(backupService.execute(BackupExecutionSource.MANUAL))
				.thenReturn(new BackupResponse(
						"tfhub_backup_2026-11-10_11-00-00.xlsx",
						java.time.OffsetDateTime.parse("2026-11-10T11:00:00+01:00"),
						null));
	}

	@Test
	void onlyAnAuthenticatedAdminCanTriggerBackup() throws Exception {
		MockHttpSession admin = authenticate("admin");
		Csrf csrf = csrfToken(admin);
		mockMvc.perform(post("/api/admin/backup")
						.session(admin)
						.cookie(csrf.cookie())
						.header("X-XSRF-TOKEN", csrf.value()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.fileName").value("tfhub_backup_2026-11-10_11-00-00.xlsx"));
		verify(backupService).execute(BackupExecutionSource.MANUAL);

		for (String username : new String[] {"coach", "athlete"}) {
			MockHttpSession session = authenticate(username);
			Csrf userCsrf = csrfToken(session);
			mockMvc.perform(post("/api/admin/backup")
							.session(session)
							.cookie(userCsrf.cookie())
							.header("X-XSRF-TOKEN", userCsrf.value()))
					.andExpect(status().isForbidden());
		}

		Csrf anonymousCsrf = csrfToken(null);
		mockMvc.perform(post("/api/admin/backup")
						.cookie(anonymousCsrf.cookie())
						.header("X-XSRF-TOKEN", anonymousCsrf.value()))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void manualBackupRequiresCsrfProtection() throws Exception {
		MockHttpSession admin = authenticate("admin");
		mockMvc.perform(post("/api/admin/backup").session(admin))
				.andExpect(status().isForbidden());
	}

	private void insertUser(String username, String role) {
		jdbcTemplate.update(
				"INSERT INTO users (username, password_hash, role) VALUES (?, ?, ?)",
				username, passwordEncoder.encode(PASSWORD), role);
	}

	private MockHttpSession authenticate(String username) throws Exception {
		Csrf csrf = csrfToken(null);
		MvcResult result = mockMvc.perform(post("/api/auth/login")
						.cookie(csrf.cookie())
						.header("X-XSRF-TOKEN", csrf.value())
						.contentType("application/json")
						.content("""
								{"username":"%s","password":"%s"}
								""".formatted(username, PASSWORD)))
				.andExpect(status().isOk())
				.andReturn();
		return (MockHttpSession) result.getRequest().getSession(false);
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

	private record Csrf(Cookie cookie, String value) {
	}
}

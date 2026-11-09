package pl.tfhub.api.result.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import pl.tfhub.api.athlete.exception.AthleteNotFoundException;
import pl.tfhub.api.result.dto.response.AthleteResultHistoryResponse;
import pl.tfhub.api.result.service.ResultService;
import pl.tfhub.api.test.dto.response.TestResponse;
import pl.tfhub.api.user.enums.UserRole;
import pl.tfhub.api.user.service.AuthenticatedUser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Testcontainers
class AthleteProfileRepositoryIntegrationTests {

	@Container
	private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

	@DynamicPropertySource
	static void configureDatabase(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		registry.add("spring.datasource.username", POSTGRES::getUsername);
		registry.add("spring.datasource.password", POSTGRES::getPassword);
	}

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private ResultService resultService;

	@BeforeEach
	void resetDatabase() {
		SecurityContextHolder.getContext().setAuthentication(
				UsernamePasswordAuthenticationToken.authenticated(
						new AuthenticatedUser(1, "test-admin", "", UserRole.ADMIN, true), null, List.of()));
		jdbcTemplate.execute("TRUNCATE TABLE test_results, athletes, tests RESTART IDENTITY CASCADE");
		insertAthlete(1, "Alice", "Able");
		insertAthlete(2, "Beth", "Baker");
		insertAthlete(3, "Cara", "Clark");
		insertTest(10, "40 m sprint", "s", "TIME", false, true);
		insertTest(11, "Long jump", "m", "DISTANCE", true, false);
		insertTest(12, "Unrecorded test", "m/s", "SPEED", true, true);
		insertResult(101, 1, 10, "2025-03-01", "5.420", "TRAINING");
		insertResult(102, 1, 10, "2025-03-01", "5.300", "COMPETITION");
		insertResult(103, 1, 11, "2025-03-02", "6.100", "TRAINING");
		insertResult(104, 1, 11, "2025-04-15", "6.250", "COMPETITION");
		insertResult(105, 2, 12, "2025-03-01", "8.500", "TRAINING");
	}

	@org.junit.jupiter.api.AfterEach
	void clearSecurityContext() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void returnsDistinctTestsWithResultsIncludingInactiveAndExcludingUnusedTests() {
		List<TestResponse> tests = resultService.getTestsWithResultsForAthlete(1);

		assertEquals(List.of(10L, 11L), tests.stream().map(TestResponse::id).toList());
		assertTrue(tests.get(0).active());
		assertFalse(tests.get(1).active());
		assertEquals("m", tests.get(1).unit());
		assertTrue(tests.stream().noneMatch(test -> test.id() == 12L));
	}

	@Test
	void filtersHistoryByMultipleTestsAndPreservesSameDayResultsAndTypes() {
		List<AthleteResultHistoryResponse> results =
				resultService.getAthleteResultHistory(1, List.of(10L, 11L), null, null);

		assertEquals(List.of(101L, 102L, 103L, 104L), results.stream()
				.map(AthleteResultHistoryResponse::id)
				.toList());
		assertEquals(LocalDate.parse("2025-03-01"), results.get(0).resultDate());
		assertEquals("40 m sprint", results.get(0).testName());
		assertEquals("s", results.get(0).testUnit());
		assertEquals(new BigDecimal("5.420"), results.get(0).value());
		assertEquals(List.of("TRAINING", "COMPETITION"), results.subList(0, 2).stream()
				.map(result -> result.type().name())
				.toList());
	}

	@Test
	void filtersHistoryByInclusiveDateRange() {
		List<AthleteResultHistoryResponse> results = resultService.getAthleteResultHistory(
				1, null, LocalDate.parse("2025-03-02"), LocalDate.parse("2025-03-02"));

		assertEquals(List.of(103L), results.stream().map(AthleteResultHistoryResponse::id).toList());
	}

	@Test
	void combinesTestIdAndDateRangeFiltersInTheDatabase() {
		List<AthleteResultHistoryResponse> results = resultService.getAthleteResultHistory(
				1, List.of(11L), LocalDate.parse("2025-03-02"), LocalDate.parse("2025-04-15"));

		assertEquals(List.of(103L, 104L), results.stream().map(AthleteResultHistoryResponse::id).toList());
		assertEquals(List.of("Long jump", "Long jump"), results.stream()
				.map(AthleteResultHistoryResponse::testName)
				.toList());
	}

	@Test
	void returnsEmptyHistoryWhenNoResultsMatch() {
		assertTrue(resultService.getAthleteResultHistory(3, null, null, null).isEmpty());
		assertTrue(resultService.getAthleteResultHistory(1, List.of(12L), null, null).isEmpty());
	}

	@Test
	void returnsNotFoundForAnUnknownAthlete() {
		assertThrows(AthleteNotFoundException.class,
				() -> resultService.getAthleteResultHistory(404, null, null, null));
		assertThrows(AthleteNotFoundException.class,
				() -> resultService.getTestsWithResultsForAthlete(404));
	}

	private void insertAthlete(int id, String firstName, String lastName) {
		jdbcTemplate.update(
				"INSERT INTO athletes (id, first_name, last_name, birth_year, gender, active) "
						+ "VALUES (?, ?, ?, 2000, 'FEMALE', TRUE)",
				id, firstName, lastName);
	}

	private void insertTest(
			int id, String name, String unit, String category, boolean higherIsBetter, boolean active) {
		jdbcTemplate.update(
				"INSERT INTO tests (id, name, unit, category, higher_is_better, active) VALUES (?, ?, ?, ?, ?, ?)",
				id, name, unit, category, higherIsBetter, active);
	}

	private void insertResult(int id, int athleteId, int testId, String date, String value, String type) {
		jdbcTemplate.update(
				"INSERT INTO test_results (id, athlete_id, test_id, result_date, value, type) VALUES (?, ?, ?, ?, ?, ?)",
				id, athleteId, testId, LocalDate.parse(date), new BigDecimal(value), type);
	}
}

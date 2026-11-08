package pl.tfhub.api.statistics.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import pl.tfhub.api.result.enums.ResultType;
import pl.tfhub.api.statistics.dto.response.DashboardOverviewResponse;
import pl.tfhub.api.statistics.service.StatisticsService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Testcontainers
class StatisticsRepositoryIntegrationTests {

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
	private StatisticsRepository statisticsRepository;

	@Autowired
	private StatisticsService statisticsService;

	@BeforeEach
	void resetDatabase() {
		jdbcTemplate.execute("TRUNCATE TABLE test_results, athletes, tests RESTART IDENTITY CASCADE");
		insertAthlete(1, "Alice", "Able", "FEMALE", true);
		insertAthlete(2, "Beth", "Baker", "FEMALE", false);
		insertAthlete(3, "Cara", "Clark", "FEMALE", true);
		insertAthlete(4, "Mia", "Mills", "MALE", false);
		insertAthlete(5, "Max", "Moore", "MALE", true);
		insertTest(10, "40 m sprint", "s", "TIME", false, true);
		insertTest(11, "Long jump", "m", "DISTANCE", true, false);
		insertTest(12, "Running speed", "m/s", "SPEED", true, true);
	}

	@Test
	void dashboardReturnsDatabaseAggregatesAndAtMostTenOrderedResultsWithInactiveLinks() {
		insertSprintRecords();
		insertResult(200, 1, 11, "2024-06-01", "6.25", "TRAINING");
		insertResult(201, 2, 11, "2026-01-01", "6.50", "COMPETITION");
		insertResult(308, 4, 10, "2026-11-08", "11.100", "COMPETITION");
		insertResult(309, 2, 11, "2026-11-08", "6.500", "TRAINING");
		insertResult(310, 1, 10, "2026-11-08", "12.000", "TRAINING");
		for (int id = 300; id < 308; id++) {
			insertResult(id, 1, 10, "2026-11-08", "12.000", id % 2 == 0 ? "TRAINING" : "COMPETITION");
		}

		DashboardOverviewResponse response = statisticsService.getDashboardOverview();

		assertEquals(3, response.activeAthleteCount());
		assertEquals(List.of(10L, 11L), response.availableTests().stream().map(test -> test.id()).toList());
		assertEquals(List.of(2026, 2025, 2024),
				response.availableSeasons());
		assertEquals(10, response.recentResults().size());
		assertEquals(List.of(310L, 309L, 308L), response.recentResults().subList(0, 3).stream()
				.map(result -> result.id()).toList());
		assertEquals("Mia Mills", response.recentResults().stream()
				.filter(result -> result.athleteId() == 4L)
				.findFirst().orElseThrow().athleteName());
		assertEquals("Beth Baker", response.recentResults().stream()
				.filter(result -> result.athleteId() == 2L)
				.findFirst().orElseThrow().athleteName());
		assertTrue(response.availableTests().stream().anyMatch(test -> !test.active()));
		assertTrue(response.recentResults().stream().anyMatch(result -> result.testId() == 11L));
		assertTrue(response.recentResults().stream().anyMatch(result -> result.type() == ResultType.TRAINING));
		assertTrue(response.recentResults().stream().anyMatch(result -> result.type() == ResultType.COMPETITION));
	}

	@Test
	void dashboardReturnsZeroAndEmptyCollectionsWhenNoDataExists() {
		jdbcTemplate.update("UPDATE athletes SET active = FALSE");
		DashboardOverviewResponse response = statisticsService.getDashboardOverview();

		assertEquals(0, response.activeAthleteCount());
		assertTrue(response.availableTests().isEmpty());
		assertTrue(response.availableSeasons().isEmpty());
		assertTrue(response.recentResults().isEmpty());
	}

	@Test
	void ranksBestTimePerAthleteWithCompetitionRankingAndCalendarYearFiltering() {
		insertSprintRecords();

		var allTime = statisticsService.getClubRecords("10", null);
		assertEquals(List.of(1L, 2L, 3L), allTime.female().stream().map(record -> record.athleteId()).toList());
		assertEquals(List.of(1, 1, 3), allTime.female().stream().map(record -> record.place()).toList());
		assertEquals(1L, allTime.female().get(0).athleteId());
		assertEquals(new BigDecimal("11.800"), allTime.female().get(1).value());
		assertEquals(LocalDate.parse("2026-03-01"), allTime.female().get(0).resultDate());
		assertEquals(List.of(4L, 5L), allTime.male().stream().map(record -> record.athleteId()).toList());
		assertEquals(1, allTime.male().get(0).place());

		var season = statisticsService.getClubRecords("10", "2025");
		assertEquals(2025, season.season());
		assertEquals(List.of(2L, 1L), season.female().stream().map(record -> record.athleteId()).toList());
		assertEquals(List.of(1, 2), season.female().stream().map(record -> record.place()).toList());
		assertEquals(1, season.male().size());
	}

	@Test
	void usesHigherIsBetterForDistanceAndSpeedAndReturnsEmptyGenderCollections() {
		insertResult(400, 1, 11, "2026-06-01", "5.20", "TRAINING");
		insertResult(401, 2, 11, "2026-06-02", "6.10", "COMPETITION");
		insertResult(402, 3, 11, "2026-06-03", "5.80", "TRAINING");
		insertResult(410, 4, 12, "2026-06-01", "8.20", "COMPETITION");
		insertResult(411, 5, 12, "2026-06-02", "8.50", "TRAINING");

		var distance = statisticsService.getClubRecords("11", null);
		assertEquals(List.of(2L, 3L, 1L), distance.female().stream().map(record -> record.athleteId()).toList());
		assertTrue(distance.male().isEmpty());

		var speed = statisticsService.getClubRecords("12", null);
		assertEquals(List.of(5L, 4L), speed.male().stream().map(record -> record.athleteId()).toList());
		assertTrue(speed.female().isEmpty());
	}

	@Test
	void returnsEmptyRecordListsForTestWithNoResults() {
		var response = statisticsService.getClubRecords("12", null);

		assertEquals(12L, response.testId());
		assertTrue(response.female().isEmpty());
		assertTrue(response.male().isEmpty());
	}

	private void insertSprintRecords() {
		insertResult(100, 1, 10, "2025-03-01", "12.500", "TRAINING");
		insertResult(101, 1, 10, "2026-02-01", "11.800", "TRAINING");
		insertResult(102, 1, 10, "2026-03-01", "11.800", "COMPETITION");
		insertResult(103, 2, 10, "2025-04-01", "11.800", "COMPETITION");
		insertResult(104, 3, 10, "2026-05-01", "13.000", "TRAINING");
		insertResult(105, 4, 10, "2026-05-01", "10.500", "COMPETITION");
		insertResult(106, 5, 10, "2025-05-01", "10.600", "TRAINING");
	}

	private void insertAthlete(int id, String firstName, String lastName, String gender, boolean active) {
		jdbcTemplate.update(
				"INSERT INTO athletes (id, first_name, last_name, birth_year, gender, active) VALUES (?, ?, ?, 2000, ?, ?)",
				id, firstName, lastName, gender, active);
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

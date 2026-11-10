package pl.tfhub.api.backup;

import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Testcontainers
class BackupExportIntegrationTests {

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
	private BackupExporter exporter;

	@TempDir
	Path temporaryDirectory;

	@BeforeEach
	void resetBusinessData() {
		jdbcTemplate.execute(
				"TRUNCATE TABLE test_results, athletes, tests, training_groups RESTART IDENTITY CASCADE");
		jdbcTemplate.update("INSERT INTO training_groups (name, coach_name) VALUES ('Group A', 'Coach A')");
		jdbcTemplate.update("""
				INSERT INTO athletes (first_name, last_name, birth_year, gender, active, training_group_id)
				VALUES ('Inactive', 'Athlete', 2001, 'MALE', FALSE, 1),
				       ('Unassigned', 'Athlete', 2002, 'FEMALE', TRUE, NULL)
				""");
		jdbcTemplate.update("""
				INSERT INTO tests (name, unit, category, higher_is_better, active)
				VALUES ('Old test', 's', 'TIME', FALSE, FALSE)
				""");
		jdbcTemplate.update("""
				INSERT INTO test_results (athlete_id, test_id, result_date, value, type)
				VALUES (1, 1, DATE '2020-01-02', 12.345, 'TRAINING'),
				       (2, 1, DATE '2026-11-09', 11.234, 'COMPETITION')
				""");
	}

	@Test
	void exportsResultsForInactiveAthletesAndCurrentNullableGroupDetails() throws Exception {
		Path workbookPath = exporter.export(
				temporaryDirectory,
				"tfhub_backup_2026-11-10_11-00-00.xlsx",
				OffsetDateTime.now(ZoneId.of("Europe/Warsaw")));

		try (var workbook = WorkbookFactory.create(workbookPath.toFile())) {
			var results = workbook.getSheet("Wyniki");
			assertEquals(3, results.getPhysicalNumberOfRows());
			assertEquals("Inactive", results.getRow(1).getCell(1).getStringCellValue());
			assertFalse(results.getRow(1).getCell(5).getBooleanCellValue());
			assertEquals("Group A", results.getRow(1).getCell(7).getStringCellValue());
			assertEquals("Coach A", results.getRow(1).getCell(8).getStringCellValue());
			assertEquals("Unassigned", results.getRow(2).getCell(1).getStringCellValue());
			assertTrue(results.getRow(2).getCell(6) == null
					|| results.getRow(2).getCell(6).getCellType()
							== org.apache.poi.ss.usermodel.CellType.BLANK);
		}
	}
}

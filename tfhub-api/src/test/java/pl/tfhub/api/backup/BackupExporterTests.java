package pl.tfhub.api.backup;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doAnswer;

class BackupExporterTests {

	@TempDir
	Path temporaryDirectory;

	@Test
	void exportsDatabaseFieldsAsTwoTypedWorksheets() throws Exception {
		BackupExportRepository repository = Mockito.mock(BackupExportRepository.class);
		doAnswer(invocation -> {
			@SuppressWarnings("unchecked")
			var consumer = (java.util.function.Consumer<BackupExportRow>) invocation.getArgument(0);
			consumer.accept(new BackupExportRow(
					9_007_199_254_740_991L, "Alice", "Runner", 2004, "FEMALE", false,
					null, null, null, 31, "Long jump", "DISTANCE", "m", true, false,
					42, LocalDate.of(2026, 11, 9), new BigDecimal("6.125"), "COMPETITION"));
			return null;
		}).when(repository).forEachResult(ArgumentMatchers.any());

		BackupExporter exporter = new BackupExporter(repository, new BackupWorkbookValidator());
		OffsetDateTime exportedAt = OffsetDateTime.of(
				2026, 11, 10, 11, 5, 6, 0, ZoneId.of("Europe/Warsaw").getRules()
						.getOffset(LocalDate.of(2026, 11, 10).atStartOfDay()));
		Path workbookPath = exporter.export(temporaryDirectory, "backup.xlsx", exportedAt);

		try (var workbook = WorkbookFactory.create(workbookPath.toFile())) {
			assertEquals(List.of("Wyniki", "Metadata"),
					List.of(workbook.getSheetAt(0).getSheetName(), workbook.getSheetAt(1).getSheetName()));
			var results = workbook.getSheet("Wyniki");
			assertEquals(List.of(
							"athlete_id", "first_name", "last_name", "birth_year", "gender", "athlete_active",
							"training_group_id", "training_group_name", "training_group_coach_name",
							"test_id", "test_name", "test_category", "test_unit", "higher_is_better", "test_active",
							"result_id", "result_date", "result_value", "result_type"),
					java.util.stream.IntStream.range(0, results.getRow(0).getLastCellNum())
							.mapToObj(index -> results.getRow(0).getCell(index).getStringCellValue())
							.toList());
			var result = results.getRow(1);
			assertEquals("9007199254740991", result.getCell(0).getStringCellValue());
			assertEquals("Alice", result.getCell(1).getStringCellValue());
			assertEquals("Runner", result.getCell(2).getStringCellValue());
			assertFalse(result.getCell(5).getBooleanCellValue());
			assertTrue(result.getCell(6) == null
					|| result.getCell(6).getCellType() == org.apache.poi.ss.usermodel.CellType.BLANK);
			assertEquals("Long jump", result.getCell(10).getStringCellValue());
			assertTrue(result.getCell(14).getCellType() == org.apache.poi.ss.usermodel.CellType.BOOLEAN);
			assertTrue(DateUtil.isCellDateFormatted(result.getCell(16)));
			assertEquals(6.125, result.getCell(17).getNumericCellValue(), 0.000001);
			assertEquals("COMPETITION", result.getCell(18).getStringCellValue());

			var metadata = workbook.getSheet("Metadata");
			assertEquals("format_version", metadata.getRow(1).getCell(0).getStringCellValue());
			assertEquals(1, metadata.getRow(1).getCell(1).getNumericCellValue(), 0);
			assertEquals("2026-11-10T11:05:06+01:00", metadata.getRow(2).getCell(1).getStringCellValue());
			assertEquals("Europe/Warsaw", metadata.getRow(3).getCell(1).getStringCellValue());
		}
	}
}

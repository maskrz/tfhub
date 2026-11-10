package pl.tfhub.api.backup;

import java.io.OutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BackupExporter {

	private static final ZoneId WARSAW = ZoneId.of("Europe/Warsaw");
	private static final String[] HEADERS = {
		"athlete_id", "first_name", "last_name", "birth_year", "gender", "athlete_active",
		"training_group_id", "training_group_name", "training_group_coach_name",
		"test_id", "test_name", "test_category", "test_unit", "higher_is_better", "test_active",
		"result_id", "result_date", "result_value", "result_type"
	};
	private static final String[] METADATA = {"format_version", "exported_at", "timezone"};

	private final BackupExportRepository repository;
	private final BackupWorkbookValidator validator;

	public BackupExporter(BackupExportRepository repository, BackupWorkbookValidator validator) {
		this.repository = repository;
		this.validator = validator;
	}

	@Transactional(readOnly = true)
	public Path export(Path directory, String fileName, OffsetDateTime exportedAt) {
		Path outputFile = directory.resolve(fileName);
		SXSSFWorkbook workbook = new SXSSFWorkbook(100);
		workbook.setCompressTempFiles(true);
		RuntimeException failure = null;
		try {
			Sheet results = workbook.createSheet("Wyniki");
			writeHeaders(results.createRow(0), HEADERS);
			DataFormat formats = workbook.createDataFormat();
			CellStyle dateStyle = workbook.createCellStyle();
			dateStyle.setDataFormat(formats.getFormat("yyyy-mm-dd"));
			CellStyle valueStyle = workbook.createCellStyle();
			valueStyle.setDataFormat(formats.getFormat("0.000"));
			int[] rowNumber = {1};
			repository.forEachResult(result -> writeResult(results.createRow(rowNumber[0]++), result, dateStyle, valueStyle));

			Sheet metadata = workbook.createSheet("Metadata");
			writeHeaders(metadata.createRow(0), new String[] {"key", "value"});
			writeText(metadata.createRow(1), 0, METADATA[0]);
			metadata.getRow(1).createCell(1).setCellValue(1);
			writeText(metadata.createRow(2), 0, METADATA[1]);
			metadata.getRow(2).createCell(1).setCellValue(DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(exportedAt));
			writeText(metadata.createRow(3), 0, METADATA[2]);
			metadata.getRow(3).createCell(1).setCellValue(WARSAW.getId());

			try (OutputStream output = Files.newOutputStream(outputFile)) {
				workbook.write(output);
			}
			validator.validate(outputFile);
			return outputFile;
		} catch (IOException exception) {
			failure = new IllegalStateException("Cannot generate XLSX backup", exception);
			throw failure;
		} catch (RuntimeException exception) {
			failure = exception;
			throw exception;
		} finally {
			IOException closeFailure = null;
			try {
				workbook.close();
			} catch (IOException exception) {
				closeFailure = exception;
			}
			workbook.dispose();
			if (closeFailure != null) {
				if (failure == null) {
					throw new IllegalStateException("Cannot close generated XLSX workbook", closeFailure);
				}
				failure.addSuppressed(closeFailure);
			}
		}
	}

	private void writeResult(Row row, BackupExportRow result, CellStyle dateStyle, CellStyle valueStyle) {
		writeText(row, 0, Long.toString(result.athleteId()));
		writeText(row, 1, result.firstName());
		writeText(row, 2, result.lastName());
		row.createCell(3).setCellValue(result.birthYear());
		writeText(row, 4, result.gender());
		row.createCell(5).setCellValue(result.athleteActive());
		writeNullableText(row, 6, result.trainingGroupId() == null ? null : result.trainingGroupId().toString());
		writeNullableText(row, 7, result.trainingGroupName());
		writeNullableText(row, 8, result.trainingGroupCoachName());
		writeText(row, 9, Long.toString(result.testId()));
		writeText(row, 10, result.testName());
		writeText(row, 11, result.testCategory());
		writeText(row, 12, result.testUnit());
		row.createCell(13).setCellValue(result.higherIsBetter());
		row.createCell(14).setCellValue(result.testActive());
		writeText(row, 15, Long.toString(result.resultId()));
		Cell date = row.createCell(16);
		date.setCellValue(DateUtil.getExcelDate(result.resultDate().atStartOfDay()));
		date.setCellStyle(dateStyle);
		Cell value = row.createCell(17);
		BigDecimal exactValue = result.resultValue();
		value.setCellValue(exactValue.doubleValue());
		value.setCellStyle(valueStyle);
		writeText(row, 18, result.resultType());
	}

	private void writeHeaders(Row row, String[] headers) {
		for (int index = 0; index < headers.length; index++) {
			writeText(row, index, headers[index]);
		}
	}

	private void writeText(Row row, int column, String value) {
		row.createCell(column).setCellValue(value);
	}

	private void writeNullableText(Row row, int column, String value) {
		if (value != null) {
			writeText(row, column, value);
		}
	}
}

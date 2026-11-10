package pl.tfhub.api.backup;

import java.math.BigDecimal;
import java.time.LocalDate;

public record BackupExportRow(
		long athleteId,
		String firstName,
		String lastName,
		int birthYear,
		String gender,
		boolean athleteActive,
		Long trainingGroupId,
		String trainingGroupName,
		String trainingGroupCoachName,
		long testId,
		String testName,
		String testCategory,
		String testUnit,
		boolean higherIsBetter,
		boolean testActive,
		long resultId,
		LocalDate resultDate,
		BigDecimal resultValue,
		String resultType) {
}

package pl.tfhub.api.statistics.repository;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ClubRecordRow(
		int place,
		Long athleteId,
		String athleteName,
		BigDecimal value,
		String unit,
		LocalDate resultDate) {
}

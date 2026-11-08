package pl.tfhub.api.statistics.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ClubRecordResponse(
		int place,
		Long athleteId,
		String athleteName,
		BigDecimal value,
		String unit,
		LocalDate resultDate) {
}

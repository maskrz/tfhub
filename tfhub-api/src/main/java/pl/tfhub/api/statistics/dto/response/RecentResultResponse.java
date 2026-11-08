package pl.tfhub.api.statistics.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import pl.tfhub.api.result.enums.ResultType;

public record RecentResultResponse(
		Long id,
		LocalDate resultDate,
		Long athleteId,
		String athleteName,
		Long testId,
		String testName,
		BigDecimal value,
		String unit,
		ResultType type) {
}

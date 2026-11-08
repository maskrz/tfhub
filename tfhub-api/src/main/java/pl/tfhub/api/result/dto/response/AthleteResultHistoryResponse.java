package pl.tfhub.api.result.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import pl.tfhub.api.result.enums.ResultType;

public record AthleteResultHistoryResponse(
		Long id,
		Long testId,
		String testName,
		String testUnit,
		LocalDate resultDate,
		BigDecimal value,
		ResultType type) {
}

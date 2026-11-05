package pl.tfhub.api.result.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import pl.tfhub.api.result.entity.TestResult;
import pl.tfhub.api.result.enums.ResultType;

public record ResultResponse(
		Long id,
		Long athleteId,
		Long testId,
		LocalDate resultDate,
		BigDecimal value,
		ResultType type) {

	public static ResultResponse from(TestResult result) {
		return new ResultResponse(
				result.getId(),
				result.getAthleteId(),
				result.getTestId(),
				result.getResultDate(),
				result.getValue(),
				result.getType());
	}
}

package pl.tfhub.api.result.dto;

import pl.tfhub.api.result.ResultType;
import pl.tfhub.api.result.TestResult;

import java.math.BigDecimal;
import java.time.LocalDate;

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

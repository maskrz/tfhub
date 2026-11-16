package pl.tfhub.api.competition.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Digits;
import java.math.BigDecimal;
import java.time.LocalDate;
import pl.tfhub.api.competition.enums.CompetitionSeasonType;

public record SaveCompetitionResultRequest(
		@NotNull @Positive Long eventId,
		@NotNull LocalDate resultDate,
		@NotBlank @Size(max = 50) String originalMark,
		@Digits(integer = 9, fraction = 3) BigDecimal numericValue,
		@Digits(integer = 4, fraction = 2) BigDecimal wind,
		Integer season,
		@NotNull CompetitionSeasonType seasonType,
		@Size(max = 250) String competitionName,
		@Size(max = 250) String location,
		@Size(max = 100) String resultRound) {

	public SaveCompetitionResultRequest {
		originalMark = originalMark == null ? null : originalMark.strip();
		competitionName = trimToNull(competitionName);
		location = trimToNull(location);
		resultRound = trimToNull(resultRound);
	}

	private static String trimToNull(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.strip();
		return trimmed.isEmpty() ? null : trimmed;
	}
}

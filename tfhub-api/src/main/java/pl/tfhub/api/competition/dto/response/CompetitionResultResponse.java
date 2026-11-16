package pl.tfhub.api.competition.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import pl.tfhub.api.competition.entity.CompetitionResult;
import pl.tfhub.api.competition.enums.CompetitionResultSource;
import pl.tfhub.api.competition.enums.CompetitionSeasonType;

public record CompetitionResultResponse(
		Long id,
		Long athleteId,
		Long eventId,
		String eventName,
		LocalDate resultDate,
		String originalMark,
		BigDecimal numericValue,
		String unit,
		BigDecimal wind,
		Integer season,
		CompetitionSeasonType seasonType,
		String competitionName,
		String location,
		String resultRound,
		CompetitionResultSource source) {

	public static CompetitionResultResponse from(CompetitionResult result) {
		return new CompetitionResultResponse(
				result.getId(),
				result.getAthleteId(),
				result.getEvent().getId(),
				result.getEvent().getName(),
				result.getResultDate(),
				result.getOriginalMark(),
				result.getNumericValue(),
				result.getUnit(),
				result.getWind(),
				result.getSeason(),
				result.getSeasonType(),
				result.getCompetitionName(),
				result.getLocation(),
				result.getResultRound(),
				result.getSource());
	}
}

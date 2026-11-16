package pl.tfhub.api.competition.pzla;

import java.math.BigDecimal;
import java.time.LocalDate;
import pl.tfhub.api.competition.enums.CompetitionSeasonType;

public record PzlaResult(
		String eventSourceId,
		String eventName,
		String unit,
		LocalDate resultDate,
		String originalMark,
		BigDecimal numericValue,
		BigDecimal wind,
		int season,
		CompetitionSeasonType seasonType,
		String competitionSourceId,
		String competitionName,
		String location,
		String resultRound,
		String sourceRecordKey,
		String sourceFingerprint) {
}

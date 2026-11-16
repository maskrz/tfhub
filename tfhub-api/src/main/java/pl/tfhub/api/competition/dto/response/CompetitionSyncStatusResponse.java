package pl.tfhub.api.competition.dto.response;

import java.time.Instant;
import pl.tfhub.api.competition.entity.CompetitionSyncRun;
import pl.tfhub.api.competition.enums.CompetitionSyncMode;
import pl.tfhub.api.competition.enums.CompetitionSyncStatus;

public record CompetitionSyncStatusResponse(
		Long runId,
		long athleteId,
		CompetitionSyncMode mode,
		CompetitionSyncStatus status,
		Instant requestedAt,
		Instant startedAt,
		Instant finishedAt,
		Instant lastSuccessfulSyncAt,
		String errorCode,
		String errorMessage) {

	public static CompetitionSyncStatusResponse from(
			CompetitionSyncRun run, Instant lastSuccessfulSyncAt) {
		return new CompetitionSyncStatusResponse(
				run.getId(),
				run.getAthleteId(),
				run.getMode(),
				run.getStatus(),
				run.getRequestedAt(),
				run.getStartedAt(),
				run.getFinishedAt(),
				lastSuccessfulSyncAt,
				run.getErrorCode(),
				run.getErrorMessage());
	}
}

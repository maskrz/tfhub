package pl.tfhub.api.competition.dto.response;

import java.util.List;

public record GroupCompetitionSyncResponse(
		long groupId,
		List<CompetitionSyncStatusResponse> submitted,
		List<SkippedAthlete> skipped) {

	public GroupCompetitionSyncResponse {
		submitted = List.copyOf(submitted);
		skipped = List.copyOf(skipped);
	}

	public record SkippedAthlete(long athleteId, String reason) {
	}
}

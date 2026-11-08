package pl.tfhub.api.statistics.dto.response;

import java.util.List;

public record DashboardOverviewResponse(
		long activeAthleteCount,
		List<AvailableTestResponse> availableTests,
		List<Integer> availableSeasons,
		List<RecentResultResponse> recentResults) {
}

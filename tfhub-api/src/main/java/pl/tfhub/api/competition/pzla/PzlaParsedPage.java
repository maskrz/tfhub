package pl.tfhub.api.competition.pzla;

import java.util.List;

public record PzlaParsedPage(int currentSeason, List<PzlaSeason> availableSeasons, List<PzlaResult> results) {

	public PzlaParsedPage {
		availableSeasons = List.copyOf(availableSeasons);
		results = List.copyOf(results);
	}
}

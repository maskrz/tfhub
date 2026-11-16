package pl.tfhub.api.competition.pzla;

public interface PzlaPageSource {

	String fetchCurrentSeason(String pzlaId);

	String fetchSeason(String pzlaId, PzlaSeason season);
}

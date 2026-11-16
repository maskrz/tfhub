package pl.tfhub.api.competition.exception;

public class CompetitionEventHasResultsException extends RuntimeException {

	public CompetitionEventHasResultsException() {
		super("Competition event unit cannot change while results reference it");
	}
}

package pl.tfhub.api.competition.exception;

public class CompetitionEventNotFoundException extends RuntimeException {

	public CompetitionEventNotFoundException(long id) {
		super("Competition event not found: " + id);
	}
}

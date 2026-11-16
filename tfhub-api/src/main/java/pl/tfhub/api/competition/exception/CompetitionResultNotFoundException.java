package pl.tfhub.api.competition.exception;

public class CompetitionResultNotFoundException extends RuntimeException {

	public CompetitionResultNotFoundException(long id) {
		super("Competition result not found: " + id);
	}
}

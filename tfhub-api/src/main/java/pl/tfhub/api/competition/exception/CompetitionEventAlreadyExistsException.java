package pl.tfhub.api.competition.exception;

public class CompetitionEventAlreadyExistsException extends RuntimeException {

	public CompetitionEventAlreadyExistsException() {
		super("Competition event already exists");
	}
}

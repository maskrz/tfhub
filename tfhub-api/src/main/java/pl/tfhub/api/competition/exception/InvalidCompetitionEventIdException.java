package pl.tfhub.api.competition.exception;

public class InvalidCompetitionEventIdException extends RuntimeException {

	public InvalidCompetitionEventIdException() {
		super("Competition event ID must be a positive integer");
	}
}

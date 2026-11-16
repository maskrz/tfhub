package pl.tfhub.api.competition.exception;

public class InvalidCompetitionResultIdException extends RuntimeException {

	public InvalidCompetitionResultIdException() {
		super("Competition result ID must be a positive integer");
	}
}

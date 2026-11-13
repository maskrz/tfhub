package pl.tfhub.api.athlete.exception;

public class InvalidAthleteAliasException extends RuntimeException {
	public InvalidAthleteAliasException() {
		super("An athlete alias must contain between 1 and 255 characters after normalization");
	}
}

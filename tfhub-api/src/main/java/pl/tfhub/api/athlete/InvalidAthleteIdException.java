package pl.tfhub.api.athlete;

public class InvalidAthleteIdException extends RuntimeException {

	public InvalidAthleteIdException() {
		super("Athlete ID must be a positive integer");
	}
}

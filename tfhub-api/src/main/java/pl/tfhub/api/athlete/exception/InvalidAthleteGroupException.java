package pl.tfhub.api.athlete.exception;

public class InvalidAthleteGroupException extends RuntimeException {

	public InvalidAthleteGroupException() {
		super("A valid assigned training group is required to create an athlete");
	}
}

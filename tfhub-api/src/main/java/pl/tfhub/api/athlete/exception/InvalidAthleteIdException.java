package pl.tfhub.api.athlete.exception;

import pl.tfhub.api.athlete.entity.Athlete;

public class InvalidAthleteIdException extends RuntimeException {

	public InvalidAthleteIdException() {
		super("Athlete ID must be a positive integer");
	}
}

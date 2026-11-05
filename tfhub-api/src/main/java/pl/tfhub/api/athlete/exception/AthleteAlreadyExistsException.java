package pl.tfhub.api.athlete.exception;



public class AthleteAlreadyExistsException extends RuntimeException {

	public AthleteAlreadyExistsException() {
		super("An athlete with the same first name, last name, and birth year already exists");
	}
}

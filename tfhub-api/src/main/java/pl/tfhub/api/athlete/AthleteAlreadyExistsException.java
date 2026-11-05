package pl.tfhub.api.athlete;

public class AthleteAlreadyExistsException extends RuntimeException {

	public AthleteAlreadyExistsException() {
		super("An athlete with the same first name, last name, and birth year already exists");
	}
}

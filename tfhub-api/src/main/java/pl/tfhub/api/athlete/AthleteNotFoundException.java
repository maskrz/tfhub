package pl.tfhub.api.athlete;

public class AthleteNotFoundException extends RuntimeException {

	public AthleteNotFoundException(long id) {
		super("Athlete with ID " + id + " was not found");
	}
}

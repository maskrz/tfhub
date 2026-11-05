package pl.tfhub.api.athlete.exception;

import pl.tfhub.api.athlete.entity.Athlete;

public class AthleteNotFoundException extends RuntimeException {

	public AthleteNotFoundException(long id) {
		super("Athlete with ID " + id + " was not found");
	}
}

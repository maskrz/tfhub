package pl.tfhub.api.result.exception;

public class InactiveAthleteException extends RuntimeException {

	public InactiveAthleteException(long athleteId) {
		super("Athlete with ID " + athleteId + " is inactive");
	}
}

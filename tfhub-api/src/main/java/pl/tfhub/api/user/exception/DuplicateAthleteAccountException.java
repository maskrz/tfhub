package pl.tfhub.api.user.exception;

public class DuplicateAthleteAccountException extends RuntimeException {

	public DuplicateAthleteAccountException() {
		super("Athlete is already assigned to an ATHLETE account");
	}
}

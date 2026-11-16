package pl.tfhub.api.athlete.exception;

public class PzlaIdConflictException extends RuntimeException {

	public PzlaIdConflictException() {
		super("PZLA athlete ID is already assigned");
	}
}

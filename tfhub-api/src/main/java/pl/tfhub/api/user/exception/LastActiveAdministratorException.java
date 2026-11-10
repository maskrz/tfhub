package pl.tfhub.api.user.exception;

public class LastActiveAdministratorException extends RuntimeException {

	public LastActiveAdministratorException() {
		super("The system must have at least one active ADMIN account");
	}
}

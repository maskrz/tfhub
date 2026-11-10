package pl.tfhub.api.user.exception;

public class IncorrectCurrentPasswordException extends RuntimeException {

	public IncorrectCurrentPasswordException() {
		super("Current password is incorrect");
	}
}

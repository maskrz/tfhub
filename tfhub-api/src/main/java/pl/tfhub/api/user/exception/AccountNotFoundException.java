package pl.tfhub.api.user.exception;

public class AccountNotFoundException extends RuntimeException {

	public AccountNotFoundException(long id) {
		super("Account with ID " + id + " was not found");
	}
}

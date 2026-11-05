package pl.tfhub.api.test.exception;



public class InvalidTestIdException extends RuntimeException {

	public InvalidTestIdException() {
		super("Test ID must be a positive integer");
	}
}

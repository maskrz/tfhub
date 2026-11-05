package pl.tfhub.api.test;

public class InvalidTestIdException extends RuntimeException {

	public InvalidTestIdException() {
		super("Test ID must be a positive integer");
	}
}

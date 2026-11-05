package pl.tfhub.api.test;

public class TestAlreadyExistsException extends RuntimeException {

	public TestAlreadyExistsException() {
		super("A test with this name already exists");
	}
}

package pl.tfhub.api.test.exception;



public class TestAlreadyExistsException extends RuntimeException {

	public TestAlreadyExistsException() {
		super("A test with this name already exists");
	}
}

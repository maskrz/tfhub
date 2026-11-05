package pl.tfhub.api.test;

public class TestNotFoundException extends RuntimeException {

	public TestNotFoundException(long id) {
		super("Test with ID " + id + " was not found");
	}
}

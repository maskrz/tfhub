package pl.tfhub.api.result;

public class InactiveTestException extends RuntimeException {

	public InactiveTestException(long testId) {
		super("Test with ID " + testId + " is inactive");
	}
}

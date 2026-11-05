package pl.tfhub.api.result.exception;



public class InactiveTestException extends RuntimeException {

	public InactiveTestException(long testId) {
		super("Test with ID " + testId + " is inactive");
	}
}

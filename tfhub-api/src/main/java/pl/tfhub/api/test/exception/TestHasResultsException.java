package pl.tfhub.api.test.exception;



public class TestHasResultsException extends RuntimeException {

	public TestHasResultsException() {
		super("Test unit or category cannot be changed because results exist");
	}
}

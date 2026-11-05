package pl.tfhub.api.result.exception;



public class ResultNotFoundException extends RuntimeException {

	public ResultNotFoundException(long id) {
		super("Result with ID " + id + " was not found");
	}
}

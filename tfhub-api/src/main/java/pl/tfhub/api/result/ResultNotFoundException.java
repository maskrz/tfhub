package pl.tfhub.api.result;

public class ResultNotFoundException extends RuntimeException {

	public ResultNotFoundException(long id) {
		super("Result with ID " + id + " was not found");
	}
}

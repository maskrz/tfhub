package pl.tfhub.api.result;

public class MultipleResultsDuplicateAthleteException extends RuntimeException {

	public MultipleResultsDuplicateAthleteException() {
		super("Each athlete may appear only once in a batch request");
	}
}

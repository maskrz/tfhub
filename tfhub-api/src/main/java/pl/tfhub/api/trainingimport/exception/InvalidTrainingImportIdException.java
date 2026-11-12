package pl.tfhub.api.trainingimport.exception;

public class InvalidTrainingImportIdException extends RuntimeException {

	public InvalidTrainingImportIdException() {
		super("Training import ID must be a positive integer");
	}
}

package pl.tfhub.api.trainingimport.exception;

public class TrainingImportNotRetryableException extends RuntimeException {

	public TrainingImportNotRetryableException() {
		super("Only failed training imports can be retried");
	}
}

package pl.tfhub.api.trainingimport.exception;

public class TrainingImportNotReviewableException extends RuntimeException {

	public TrainingImportNotReviewableException() {
		super("Training import must be in REVIEW_REQUIRED state for this operation");
	}
}

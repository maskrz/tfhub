package pl.tfhub.api.trainingimport.exception;

public class TrainingImportNotReviewableException extends RuntimeException {

	public TrainingImportNotReviewableException() {
		super("Training import can only be reviewed in REVIEW_REQUIRED state");
	}
}

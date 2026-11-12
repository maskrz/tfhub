package pl.tfhub.api.trainingimport.exception;

public class TrainingImportDraftNotFoundException extends RuntimeException {

	public TrainingImportDraftNotFoundException(long importId) {
		super("Training import draft not found");
	}
}

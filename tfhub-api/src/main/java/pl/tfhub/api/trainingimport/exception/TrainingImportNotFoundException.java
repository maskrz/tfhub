package pl.tfhub.api.trainingimport.exception;

public class TrainingImportNotFoundException extends RuntimeException {

	public TrainingImportNotFoundException(long importId) {
		super("Training import not found");
	}
}

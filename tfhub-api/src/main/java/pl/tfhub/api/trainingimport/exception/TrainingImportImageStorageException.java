package pl.tfhub.api.trainingimport.exception;

public class TrainingImportImageStorageException extends RuntimeException {

	public TrainingImportImageStorageException(Throwable cause) {
		super("Training import image storage failed", cause);
	}
}

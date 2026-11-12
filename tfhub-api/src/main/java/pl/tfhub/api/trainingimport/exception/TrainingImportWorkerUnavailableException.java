package pl.tfhub.api.trainingimport.exception;

public class TrainingImportWorkerUnavailableException extends RuntimeException {

	public TrainingImportWorkerUnavailableException() {
		super("The training import worker is busy; retry the request shortly");
	}
}

package pl.tfhub.api.traininggroup.exception;

public class TrainingGroupNotFoundException extends RuntimeException {

	public TrainingGroupNotFoundException(long id) {
		super("Training group with ID " + id + " was not found");
	}
}

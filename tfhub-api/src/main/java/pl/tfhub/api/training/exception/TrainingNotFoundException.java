package pl.tfhub.api.training.exception;

public class TrainingNotFoundException extends RuntimeException {

    public TrainingNotFoundException(long id) {
        super("Training with ID " + id + " was not found");
    }
}

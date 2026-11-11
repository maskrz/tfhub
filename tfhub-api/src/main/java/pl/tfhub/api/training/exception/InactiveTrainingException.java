package pl.tfhub.api.training.exception;

public class InactiveTrainingException extends RuntimeException {

    public InactiveTrainingException(long id) {
        super("Training with ID " + id + " is inactive");
    }
}

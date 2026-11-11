package pl.tfhub.api.training.exception;

public class InvalidTrainingIdException extends RuntimeException {

    public InvalidTrainingIdException() {
        super("Training ID must be a positive integer");
    }
}

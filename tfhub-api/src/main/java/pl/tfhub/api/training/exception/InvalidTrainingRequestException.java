package pl.tfhub.api.training.exception;

public class InvalidTrainingRequestException extends RuntimeException {

    public InvalidTrainingRequestException(String message) {
        super(message);
    }
}

package pl.tfhub.api.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import pl.tfhub.api.athlete.AthleteAlreadyExistsException;
import pl.tfhub.api.athlete.AthleteNotFoundException;
import pl.tfhub.api.athlete.InvalidAthleteIdException;
import pl.tfhub.api.test.TestAlreadyExistsException;

import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class ApiExceptionHandler {

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ProblemDetail handleValidation(MethodArgumentNotValidException exception) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(
				HttpStatus.BAD_REQUEST, "Request validation failed");
		problem.setTitle("Invalid request");
		Map<String, String> errors = exception.getBindingResult().getFieldErrors().stream()
				.collect(Collectors.toMap(
						error -> error.getField(),
						error -> error.getDefaultMessage() == null ? "is invalid" : error.getDefaultMessage(),
						(first, second) -> first));
		problem.setProperty("errors", errors);
		return problem;
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ProblemDetail handleUnreadableRequest() {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(
				HttpStatus.BAD_REQUEST, "Request body contains invalid or malformed values");
		problem.setTitle("Invalid request");
		return problem;
	}

	@ExceptionHandler(AthleteAlreadyExistsException.class)
	public ProblemDetail handleDuplicateAthlete(AthleteAlreadyExistsException exception) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
		problem.setTitle("Athlete already exists");
		return problem;
	}

	@ExceptionHandler(TestAlreadyExistsException.class)
	public ProblemDetail handleDuplicateTest(TestAlreadyExistsException exception) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
		problem.setTitle("Test already exists");
		return problem;
	}

	@ExceptionHandler(AthleteNotFoundException.class)
	public ProblemDetail handleAthleteNotFound(AthleteNotFoundException exception) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
		problem.setTitle("Athlete not found");
		return problem;
	}

	@ExceptionHandler({InvalidAthleteIdException.class, MethodArgumentTypeMismatchException.class})
	public ProblemDetail handleInvalidAthleteId() {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(
				HttpStatus.BAD_REQUEST, "Athlete ID must be a positive integer");
		problem.setTitle("Invalid athlete ID");
		return problem;
	}
}

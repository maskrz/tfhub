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
import pl.tfhub.api.result.InactiveTestException;
import pl.tfhub.api.result.InvalidResultIdException;
import pl.tfhub.api.result.MultipleResultsDuplicateAthleteException;
import pl.tfhub.api.result.ResultNotFoundException;
import pl.tfhub.api.test.TestAlreadyExistsException;
import pl.tfhub.api.test.InvalidTestIdException;
import pl.tfhub.api.test.TestHasResultsException;
import pl.tfhub.api.test.TestNotFoundException;

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

	@ExceptionHandler(InactiveTestException.class)
	public ProblemDetail handleInactiveTest(InactiveTestException exception) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
		problem.setTitle("Test is inactive");
		return problem;
	}

	@ExceptionHandler(ResultNotFoundException.class)
	public ProblemDetail handleResultNotFound(ResultNotFoundException exception) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
		problem.setTitle("Result not found");
		return problem;
	}

	@ExceptionHandler(InvalidResultIdException.class)
	public ProblemDetail handleInvalidResultId() {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(
				HttpStatus.BAD_REQUEST, "Result ID must be a positive integer");
		problem.setTitle("Invalid result ID");
		return problem;
	}

	@ExceptionHandler(MultipleResultsDuplicateAthleteException.class)
	public ProblemDetail handleDuplicateAthleteInResultBatch(MultipleResultsDuplicateAthleteException exception) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
		problem.setTitle("Invalid result batch");
		return problem;
	}

	@ExceptionHandler(TestHasResultsException.class)
	public ProblemDetail handleTestHasResults(TestHasResultsException exception) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
		problem.setTitle("Test configuration cannot be changed");
		return problem;
	}

	@ExceptionHandler(TestNotFoundException.class)
	public ProblemDetail handleTestNotFound(TestNotFoundException exception) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
		problem.setTitle("Test not found");
		return problem;
	}

	@ExceptionHandler(InvalidTestIdException.class)
	public ProblemDetail handleInvalidTestId() {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(
				HttpStatus.BAD_REQUEST, "Test ID must be a positive integer");
		problem.setTitle("Invalid test ID");
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

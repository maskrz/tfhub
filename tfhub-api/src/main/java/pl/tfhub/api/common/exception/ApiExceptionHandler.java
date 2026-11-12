package pl.tfhub.api.common.exception;

import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import pl.tfhub.api.athlete.entity.Athlete;
import pl.tfhub.api.athlete.exception.AthleteAlreadyExistsException;
import pl.tfhub.api.athlete.exception.AthleteNotFoundException;
import pl.tfhub.api.athlete.exception.InvalidAthleteIdException;
import pl.tfhub.api.athlete.exception.InvalidAthleteGroupException;
import pl.tfhub.api.user.exception.AccountNotFoundException;
import pl.tfhub.api.user.exception.DuplicateAthleteAccountException;
import pl.tfhub.api.user.exception.DuplicateUsernameException;
import pl.tfhub.api.user.exception.InvalidAccountAssignmentException;
import pl.tfhub.api.user.exception.LastActiveAdministratorException;
import pl.tfhub.api.result.exception.InactiveAthleteException;
import pl.tfhub.api.result.exception.InactiveTestException;
import pl.tfhub.api.result.exception.InvalidResultIdException;
import pl.tfhub.api.result.exception.InvalidResultSearchException;
import pl.tfhub.api.result.exception.MultipleResultsDuplicateAthleteException;
import pl.tfhub.api.result.exception.ResultNotFoundException;
import pl.tfhub.api.statistics.exception.InvalidStatisticsParameterException;
import pl.tfhub.api.statistics.exception.StatisticsTestNotFoundException;
import pl.tfhub.api.test.exception.InvalidTestIdException;
import pl.tfhub.api.test.exception.TestAlreadyExistsException;
import pl.tfhub.api.test.exception.TestHasResultsException;
import pl.tfhub.api.test.exception.TestNotFoundException;
import pl.tfhub.api.traininggroup.exception.TrainingGroupNotFoundException;
import pl.tfhub.api.training.exception.InvalidTrainingRequestException;
import pl.tfhub.api.training.exception.InvalidTrainingIdException;
import pl.tfhub.api.training.exception.TrainingNotFoundException;
import pl.tfhub.api.user.exception.IncorrectCurrentPasswordException;
import pl.tfhub.api.backup.BackupAlreadyRunningException;
import pl.tfhub.api.backup.BackupExecutionException;

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

	@ExceptionHandler(IncorrectCurrentPasswordException.class)
	public ProblemDetail handleIncorrectCurrentPassword() {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(
				HttpStatus.BAD_REQUEST, "Current password is incorrect");
		problem.setTitle("Incorrect current password");
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

	@ExceptionHandler(InactiveAthleteException.class)
	public ProblemDetail handleInactiveAthlete(InactiveAthleteException exception) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
		problem.setTitle("Athlete is inactive");
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

	@ExceptionHandler(InvalidResultSearchException.class)
	public ProblemDetail handleInvalidResultSearch() {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(
				HttpStatus.BAD_REQUEST, "Result search parameters are invalid");
		problem.setTitle("Invalid result search");
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

	@ExceptionHandler(InvalidAthleteGroupException.class)
	public ProblemDetail handleInvalidAthleteGroup(InvalidAthleteGroupException exception) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
		problem.setTitle("Training group is required");
		return problem;
	}

	@ExceptionHandler(TrainingGroupNotFoundException.class)
	public ProblemDetail handleTrainingGroupNotFound(TrainingGroupNotFoundException exception) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
		problem.setTitle("Training group not found");
		return problem;
	}

	@ExceptionHandler(InvalidTrainingRequestException.class)
	public ProblemDetail handleInvalidTrainingRequest(InvalidTrainingRequestException exception) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
		problem.setTitle("Invalid training");
		return problem;
	}

	@ExceptionHandler(TrainingNotFoundException.class)
	public ProblemDetail handleTrainingNotFound(TrainingNotFoundException exception) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
		problem.setTitle("Training not found");
		return problem;
	}

	@ExceptionHandler(InvalidTrainingIdException.class)
	public ProblemDetail handleInvalidTrainingId() {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(
				HttpStatus.BAD_REQUEST, "Training ID must be a positive integer");
		problem.setTitle("Invalid training ID");
		return problem;
	}

	@ExceptionHandler(AccountNotFoundException.class)
	public ProblemDetail handleAccountNotFound(AccountNotFoundException exception) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
		problem.setTitle("Account not found");
		return problem;
	}

	@ExceptionHandler({DuplicateUsernameException.class, DuplicateAthleteAccountException.class})
	public ProblemDetail handleAccountConflict(RuntimeException exception) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
		problem.setTitle("Account conflict");
		return problem;
	}

	@ExceptionHandler(LastActiveAdministratorException.class)
	public ProblemDetail handleLastActiveAdministrator(LastActiveAdministratorException exception) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
		problem.setTitle("Administrator required");
		return problem;
	}

	@ExceptionHandler(InvalidAccountAssignmentException.class)
	public ProblemDetail handleInvalidAccountAssignment(InvalidAccountAssignmentException exception) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
		problem.setTitle("Invalid account assignments");
		return problem;
	}

	@ExceptionHandler(InvalidAthleteIdException.class)
	public ProblemDetail handleInvalidAthleteId() {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(
				HttpStatus.BAD_REQUEST, "Athlete ID must be a positive integer");
		problem.setTitle("Invalid athlete ID");
		return problem;
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
		if ("trainingId".equals(exception.getName())) {
			return handleInvalidTrainingId();
		}
		if ("id".equals(exception.getName())
				&& (Long.class.equals(exception.getRequiredType())
						|| long.class.equals(exception.getRequiredType()))) {
			return handleInvalidAthleteId();
		}
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(
				HttpStatus.BAD_REQUEST, "Request parameter has an invalid value");
		problem.setTitle("Invalid request parameter");
		return problem;
	}

	@ExceptionHandler(InvalidStatisticsParameterException.class)
	public ProblemDetail handleInvalidStatisticsParameter() {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(
				HttpStatus.BAD_REQUEST, "Statistics query parameters are invalid");
		problem.setTitle("Invalid statistics parameter");
		return problem;
	}

	@ExceptionHandler(StatisticsTestNotFoundException.class)
	public ProblemDetail handleStatisticsTestNotFound(StatisticsTestNotFoundException exception) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
		problem.setTitle("Test not found");
		return problem;
	}

	@ExceptionHandler(BackupAlreadyRunningException.class)
	public ProblemDetail handleBackupAlreadyRunning() {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(
				HttpStatus.CONFLICT, "Another backup operation is already running");
		problem.setTitle("Backup already running");
		return problem;
	}

	@ExceptionHandler(BackupExecutionException.class)
	public ProblemDetail handleBackupFailure() {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(
				HttpStatus.BAD_GATEWAY, "The backup operation could not be completed");
		problem.setTitle("Backup failed");
		return problem;
	}
}

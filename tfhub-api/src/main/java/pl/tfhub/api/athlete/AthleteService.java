package pl.tfhub.api.athlete;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.tfhub.api.athlete.dto.AthleteRequest;
import pl.tfhub.api.athlete.dto.AthleteResponse;

@Service
public class AthleteService {

	private static final String UNIQUE_CONSTRAINT = "uq_athletes_name_birth_year";

	private final AthleteRepository athleteRepository;

	public AthleteService(AthleteRepository athleteRepository) {
		this.athleteRepository = athleteRepository;
	}

	@Transactional
	public AthleteResponse createAthlete(AthleteRequest request) {
		if (athleteRepository.existsByFirstNameAndLastNameAndBirthYear(
				request.firstName(), request.lastName(), request.birthYear())) {
			throw new AthleteAlreadyExistsException();
		}

		Athlete athlete = new Athlete(
				request.firstName(),
				request.lastName(),
				request.birthYear(),
				request.gender());

		try {
			return AthleteResponse.from(athleteRepository.saveAndFlush(athlete));
		} catch (DataIntegrityViolationException exception) {
			if (isDuplicateAthleteConstraint(exception)) {
				throw new AthleteAlreadyExistsException();
			}
			throw exception;
		}
	}

	@Transactional(readOnly = true)
	public AthleteResponse getAthlete(long id) {
		Athlete athlete = athleteRepository.findById(id)
				.orElseThrow(() -> new AthleteNotFoundException(id));
		return AthleteResponse.from(athlete);
	}

	private boolean isDuplicateAthleteConstraint(Throwable exception) {
		Throwable cause = exception;
		while (cause != null) {
			if (cause instanceof ConstraintViolationException violation
					&& UNIQUE_CONSTRAINT.equals(violation.getConstraintName())) {
				return true;
			}
			cause = cause.getCause();
		}
		return false;
	}
}

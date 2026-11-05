package pl.tfhub.api.athlete;

import jakarta.persistence.criteria.Predicate;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.tfhub.api.athlete.dto.CreateAthleteRequest;
import pl.tfhub.api.athlete.dto.AthleteResponse;
import pl.tfhub.api.athlete.dto.SearchAthletesRequest;
import pl.tfhub.api.athlete.dto.UpdateAthleteRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class AthleteService {

	private static final String UNIQUE_CONSTRAINT = "uq_athletes_name_birth_year";

	private final AthleteRepository athleteRepository;

	public AthleteService(AthleteRepository athleteRepository) {
		this.athleteRepository = athleteRepository;
	}

	@Transactional
	public AthleteResponse createAthlete(CreateAthleteRequest request) {
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

	@Transactional
	public AthleteResponse updateAthlete(long id, UpdateAthleteRequest request) {
		Athlete athlete = athleteRepository.findById(id)
				.orElseThrow(() -> new AthleteNotFoundException(id));

		if (athleteRepository.existsByFirstNameAndLastNameAndBirthYearAndIdNot(
				request.firstName(), request.lastName(), request.birthYear(), id)) {
			throw new AthleteAlreadyExistsException();
		}

		athlete.updatePersonalInformation(
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
	public List<AthleteResponse> searchAthletes(SearchAthletesRequest request) {
		Specification<Athlete> specification = (root, query, criteriaBuilder) -> {
			List<Predicate> predicates = new ArrayList<>();
			if (request.firstName() != null) {
				predicates.add(criteriaBuilder.like(
						criteriaBuilder.lower(root.get("firstName")),
						"%" + request.firstName().toLowerCase(Locale.ROOT) + "%"));
			}
			if (request.lastName() != null) {
				predicates.add(criteriaBuilder.like(
						criteriaBuilder.lower(root.get("lastName")),
						"%" + request.lastName().toLowerCase(Locale.ROOT) + "%"));
			}
			if (request.active() != null) {
				predicates.add(criteriaBuilder.equal(root.get("active"), request.active()));
			}
			if (request.gender() != null) {
				predicates.add(criteriaBuilder.equal(root.get("gender"), request.gender()));
			}
			if (request.birthYear() != null) {
				predicates.add(criteriaBuilder.equal(root.get("birthYear"), request.birthYear()));
			}
			return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
		};

		return athleteRepository.findAll(specification)
				.stream()
				.map(AthleteResponse::from)
				.toList();
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

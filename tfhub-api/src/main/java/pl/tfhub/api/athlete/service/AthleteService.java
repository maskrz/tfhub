package pl.tfhub.api.athlete.service;

import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.tfhub.api.athlete.dto.request.ChangeAthleteStatusRequest;
import pl.tfhub.api.athlete.dto.request.CreateAthleteRequest;
import pl.tfhub.api.athlete.dto.request.SearchAthletesRequest;
import pl.tfhub.api.athlete.dto.request.UpdateAthleteRequest;
import pl.tfhub.api.athlete.dto.response.AthleteResponse;
import pl.tfhub.api.athlete.entity.Athlete;
import pl.tfhub.api.athlete.exception.AthleteAlreadyExistsException;
import pl.tfhub.api.athlete.exception.AthleteNotFoundException;
import pl.tfhub.api.athlete.repository.AthleteRepository;
import pl.tfhub.api.common.authorization.AccessScopeService;
import pl.tfhub.api.traininggroup.entity.TrainingGroup;
import pl.tfhub.api.traininggroup.exception.TrainingGroupNotFoundException;
import pl.tfhub.api.traininggroup.repository.TrainingGroupRepository;

@Service
public class AthleteService {

	private static final String UNIQUE_CONSTRAINT = "uq_athletes_name_birth_year";

	private final AthleteRepository athleteRepository;
	private final TrainingGroupRepository trainingGroupRepository;
	private final AccessScopeService accessScopeService;

	@Autowired
	public AthleteService(
			AthleteRepository athleteRepository,
			TrainingGroupRepository trainingGroupRepository,
			AccessScopeService accessScopeService) {
		this.athleteRepository = athleteRepository;
		this.trainingGroupRepository = trainingGroupRepository;
		this.accessScopeService = accessScopeService;
	}

	@Transactional
	public AthleteResponse createAthlete(CreateAthleteRequest request) {
		accessScopeService.requireCoachOrAdmin();
		accessScopeService.requireGroupWhenMissing(request.trainingGroupId() != null);
		TrainingGroup trainingGroup = null;
		if (request.trainingGroupId() != null) {
			accessScopeService.requireGroupAssignment(request.trainingGroupId());
			trainingGroup = trainingGroupRepository.findById(request.trainingGroupId())
					.orElseThrow(() -> new TrainingGroupNotFoundException(request.trainingGroupId()));
		}
		if (athleteRepository.existsByFirstNameAndLastNameAndBirthYear(
				request.firstName(), request.lastName(), request.birthYear())) {
			throw new AthleteAlreadyExistsException();
		}

		Athlete athlete = new Athlete(
				request.firstName(),
				request.lastName(),
				request.birthYear(),
				request.gender(),
				trainingGroup);

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
		accessScopeService.requireAthleteAccess(id);
		Athlete athlete = athleteRepository.findById(id)
				.orElseThrow(() -> new AthleteNotFoundException(id));
		return AthleteResponse.from(athlete);
	}

	@Transactional
	public AthleteResponse updateAthlete(long id, UpdateAthleteRequest request) {
		accessScopeService.requireCoachOrAdmin();
		accessScopeService.requireAthleteWriteAccess(id);
		Athlete athlete = athleteRepository.findById(id)
				.orElseThrow(() -> new AthleteNotFoundException(id));

		if (athleteRepository.existsByFirstNameAndLastNameAndBirthYearAndIdNot(
				request.firstName(), request.lastName(), request.birthYear(), id)) {
			throw new AthleteAlreadyExistsException();
		}

		TrainingGroup newGroup = null;
		if (request.trainingGroupId() != null) {
			accessScopeService.requireGroupAssignment(request.trainingGroupId());
			newGroup = trainingGroupRepository.findById(request.trainingGroupId())
					.orElseThrow(() -> new TrainingGroupNotFoundException(request.trainingGroupId()));
		}

		athlete.updatePersonalInformation(
				request.firstName(),
				request.lastName(),
				request.birthYear(),
				request.gender());
		if (newGroup != null) {
			athlete.changeTrainingGroup(newGroup);
		}

		try {
			return AthleteResponse.from(athleteRepository.saveAndFlush(athlete));
		} catch (DataIntegrityViolationException exception) {
			if (isDuplicateAthleteConstraint(exception)) {
				throw new AthleteAlreadyExistsException();
			}
			throw exception;
		}
	}

	@Transactional
	public AthleteResponse changeAthleteStatus(long id, ChangeAthleteStatusRequest request) {
		accessScopeService.requireCoachOrAdmin();
		accessScopeService.requireAthleteWriteAccess(id);
		Athlete athlete = athleteRepository.findById(id)
				.orElseThrow(() -> new AthleteNotFoundException(id));
		athlete.changeStatus(request.active());
		return AthleteResponse.from(athleteRepository.saveAndFlush(athlete));
	}

	@Transactional(readOnly = true)
	public List<AthleteResponse> searchAthletes(SearchAthletesRequest request) {
		accessScopeService.requireCoachOrAdmin();
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
			if (!accessScopeService.isAdmin()) {
				List<Long> groupIds = accessScopeService.querySafeIds(accessScopeService.assignedGroupIds());
				predicates.add(root.get("trainingGroup").get("id").in(groupIds));
			}
			return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
		};

		Sort sort = Sort.by(
				Sort.Order.asc("lastName").ignoreCase(),
				Sort.Order.asc("firstName").ignoreCase(),
				Sort.Order.asc("id"));
		return athleteRepository.findAll(specification, sort)
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

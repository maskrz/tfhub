package pl.tfhub.api.athlete.service;

import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.stream.Collectors;
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
import pl.tfhub.api.athlete.entity.AthleteAlias;
import pl.tfhub.api.athlete.exception.AthleteAlreadyExistsException;
import pl.tfhub.api.athlete.exception.AthleteAliasConflictException;
import pl.tfhub.api.athlete.exception.AthleteNotFoundException;
import pl.tfhub.api.athlete.exception.InvalidAthleteAliasException;
import pl.tfhub.api.athlete.repository.AthleteAliasRepository;
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
	private final AthleteAliasRepository athleteAliasRepository;

	@Autowired
	public AthleteService(
			AthleteRepository athleteRepository,
			TrainingGroupRepository trainingGroupRepository,
			AccessScopeService accessScopeService,
			AthleteAliasRepository athleteAliasRepository) {
		this.athleteRepository = athleteRepository;
		this.trainingGroupRepository = trainingGroupRepository;
		this.accessScopeService = accessScopeService;
		this.athleteAliasRepository = athleteAliasRepository;
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
			List<String> aliases = validateAliases(request.aliases());
			Athlete saved = athleteRepository.saveAndFlush(athlete);
			replaceAliases(saved, aliases);
			return AthleteResponse.from(saved, aliases);
		} catch (DataIntegrityViolationException exception) {
			throw translateConstraint(exception);
		}
	}

	@Transactional(readOnly = true)
	public AthleteResponse getAthlete(long id) {
		accessScopeService.requireAthleteAccess(id);
		Athlete athlete = athleteRepository.findById(id)
				.orElseThrow(() -> new AthleteNotFoundException(id));
		return AthleteResponse.from(athlete, aliasesFor(List.of(athlete)).getOrDefault(id, List.of()));
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

		try {
			List<String> aliases = request.aliases() == null ? null : validateAliases(request.aliases());
			if (aliases != null) {
				athleteAliasRepository.deleteByAthleteId(id);
				athleteAliasRepository.flush();
			}
			athlete.updatePersonalInformation(
					request.firstName(), request.lastName(), request.birthYear(), request.gender());
			if (newGroup != null) {
				athlete.changeTrainingGroup(newGroup);
			}
			Athlete saved = athleteRepository.saveAndFlush(athlete);
			if (aliases != null) {
				replaceAliases(saved, aliases);
			} else {
				aliases = aliasesFor(List.of(saved)).getOrDefault(id, List.of());
			}
			return AthleteResponse.from(saved, aliases);
		} catch (DataIntegrityViolationException exception) {
			throw translateConstraint(exception);
		}
	}

	@Transactional
	public AthleteResponse changeAthleteStatus(long id, ChangeAthleteStatusRequest request) {
		accessScopeService.requireCoachOrAdmin();
		accessScopeService.requireAthleteWriteAccess(id);
		Athlete athlete = athleteRepository.findById(id)
				.orElseThrow(() -> new AthleteNotFoundException(id));
		athlete.changeStatus(request.active());
		try {
			Athlete saved = athleteRepository.saveAndFlush(athlete);
			return AthleteResponse.from(saved, aliasesFor(List.of(saved)).getOrDefault(id, List.of()));
		} catch (DataIntegrityViolationException exception) {
			throw translateConstraint(exception);
		}
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
		List<Athlete> athletes = athleteRepository.findAll(specification, sort);
		Map<Long, List<String>> aliases = aliasesFor(athletes);
		return athletes.stream()
				.map(athlete -> AthleteResponse.from(athlete, aliases.getOrDefault(athlete.getId(), List.of())))
				.toList();
	}

	private Map<Long, List<String>> aliasesFor(List<Athlete> athletes) {
		List<Long> ids = athletes.stream().map(Athlete::getId).filter(java.util.Objects::nonNull).toList();
		if (ids.isEmpty()) {
			return Map.of();
		}
		return athleteAliasRepository.findByAthleteIdInOrderByIdAsc(ids).stream()
				.collect(Collectors.groupingBy(alias -> alias.getAthlete().getId(),
						Collectors.mapping(AthleteAlias::getAlias, Collectors.toList())));
	}

	@Transactional(readOnly = true)
	public List<ImportCandidate> findImportCandidates(long groupId) {
		Map<Long, ImportCandidate> candidates = new LinkedHashMap<>();
		for (AthleteRepository.ImportCandidateRow row : athleteRepository.findImportCandidateRows(groupId)) {
			ImportCandidate candidate = candidates.computeIfAbsent(row.getId(), id -> new ImportCandidate(
					id, row.getFirstName(), row.getLastName(), row.getNormalizedFullName(), new ArrayList<>()));
			if (row.getNormalizedAlias() != null) {
				candidate.normalizedAliases().add(row.getNormalizedAlias());
			}
		}
		return candidates.values().stream().map(candidate -> new ImportCandidate(
				candidate.id(), candidate.firstName(), candidate.lastName(), candidate.normalizedFullName(),
				List.copyOf(candidate.normalizedAliases()))).toList();
	}

	@Transactional(readOnly = true)
	public String normalizeImportName(String name) {
		return athleteAliasRepository.normalize(name);
	}

	public record ImportCandidate(long id, String firstName, String lastName,
			String normalizedFullName, List<String> normalizedAliases) {
	}

	private List<String> validateAliases(List<String> aliases) {
		if (aliases == null) {
			return List.of();
		}
		Set<String> keys = new HashSet<>();
		for (String alias : aliases) {
			String key = athleteAliasRepository.normalize(alias);
			if (key == null || key.isEmpty() || key.codePointCount(0, key.length()) > 255) {
				throw new InvalidAthleteAliasException();
			}
			if (!keys.add(key)) {
				throw new AthleteAliasConflictException();
			}
		}
		return List.copyOf(aliases);
	}

	private void replaceAliases(Athlete athlete, List<String> aliases) {
		if (!aliases.isEmpty()) {
			athleteAliasRepository.saveAllAndFlush(aliases.stream()
					.map(alias -> new AthleteAlias(athlete, alias)).toList());
		}
	}

	private RuntimeException translateConstraint(DataIntegrityViolationException exception) {
		Throwable cause = exception;
		while (cause != null) {
			if (cause instanceof ConstraintViolationException violation) {
				String name = violation.getConstraintName();
				if (UNIQUE_CONSTRAINT.equals(name)) {
					return new AthleteAlreadyExistsException();
				}
				if ("uq_athlete_alias_active_group".equals(name) || "uq_athlete_alias_athlete".equals(name)) {
					return new AthleteAliasConflictException();
				}
			}
			cause = cause.getCause();
		}
		return exception;
	}
}

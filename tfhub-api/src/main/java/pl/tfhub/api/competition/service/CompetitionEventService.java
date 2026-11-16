package pl.tfhub.api.competition.service;

import java.util.List;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.tfhub.api.common.authorization.AccessScopeService;
import pl.tfhub.api.competition.dto.request.ChangeCompetitionEventStatusRequest;
import pl.tfhub.api.competition.dto.request.SaveCompetitionEventRequest;
import pl.tfhub.api.competition.dto.response.CompetitionEventResponse;
import pl.tfhub.api.competition.entity.CompetitionEvent;
import pl.tfhub.api.competition.exception.CompetitionEventAlreadyExistsException;
import pl.tfhub.api.competition.exception.CompetitionEventHasResultsException;
import pl.tfhub.api.competition.exception.CompetitionEventNotFoundException;
import pl.tfhub.api.competition.repository.CompetitionEventRepository;

@Service
public class CompetitionEventService {

	private static final String UNIQUE_CONSTRAINT = "uq_competition_events_normalized_name";

	private final CompetitionEventRepository eventRepository;
	private final AccessScopeService accessScopeService;

	public CompetitionEventService(
			CompetitionEventRepository eventRepository,
			AccessScopeService accessScopeService) {
		this.eventRepository = eventRepository;
		this.accessScopeService = accessScopeService;
	}

	@Transactional(readOnly = true)
	public List<CompetitionEventResponse> getEvents() {
		accessScopeService.requireAdmin();
		return eventRepository.findAllByOrderByNameAsc().stream()
				.map(CompetitionEventResponse::from)
				.toList();
	}

	@Transactional
	public CompetitionEventResponse createEvent(SaveCompetitionEventRequest request) {
		accessScopeService.requireAdmin();
		if (eventRepository.existsByNormalizedName(request.name())) {
			throw new CompetitionEventAlreadyExistsException();
		}
		try {
			return CompetitionEventResponse.from(
					eventRepository.saveAndFlush(new CompetitionEvent(request.name(), request.unit())));
		} catch (DataIntegrityViolationException exception) {
			if (hasConstraint(exception, UNIQUE_CONSTRAINT)) {
				throw new CompetitionEventAlreadyExistsException();
			}
			throw exception;
		}
	}

	@Transactional
	public CompetitionEventResponse updateEvent(long id, SaveCompetitionEventRequest request) {
		accessScopeService.requireAdmin();
		CompetitionEvent event = findEvent(id);
		if (eventRepository.existsByNormalizedNameAndIdNot(request.name(), id)) {
			throw new CompetitionEventAlreadyExistsException();
		}
		if (!event.getUnit().equals(request.unit()) && eventRepository.existsResultsByEventId(id)) {
			throw new CompetitionEventHasResultsException();
		}
		event.update(request.name(), request.unit());
		try {
			return CompetitionEventResponse.from(eventRepository.saveAndFlush(event));
		} catch (DataIntegrityViolationException exception) {
			if (hasConstraint(exception, UNIQUE_CONSTRAINT)) {
				throw new CompetitionEventAlreadyExistsException();
			}
			throw exception;
		}
	}

	@Transactional
	public CompetitionEventResponse changeStatus(long id, ChangeCompetitionEventStatusRequest request) {
		accessScopeService.requireAdmin();
		CompetitionEvent event = findEvent(id);
		event.changeStatus(request.active());
		return CompetitionEventResponse.from(eventRepository.saveAndFlush(event));
	}

	CompetitionEvent findEvent(long id) {
		return eventRepository.findById(id).orElseThrow(() -> new CompetitionEventNotFoundException(id));
	}

	private boolean hasConstraint(Throwable exception, String constraintName) {
		Throwable cause = exception;
		while (cause != null) {
			if (cause instanceof ConstraintViolationException violation
					&& constraintName.equals(violation.getConstraintName())) {
				return true;
			}
			cause = cause.getCause();
		}
		return false;
	}
}

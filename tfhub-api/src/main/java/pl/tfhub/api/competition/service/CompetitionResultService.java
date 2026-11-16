package pl.tfhub.api.competition.service;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.tfhub.api.athlete.service.AthleteService;
import pl.tfhub.api.common.authorization.AccessScopeService;
import pl.tfhub.api.competition.dto.request.SaveCompetitionResultRequest;
import pl.tfhub.api.competition.dto.response.CompetitionResultResponse;
import pl.tfhub.api.competition.entity.CompetitionEvent;
import pl.tfhub.api.competition.entity.CompetitionResult;
import pl.tfhub.api.competition.enums.CompetitionResultSource;
import pl.tfhub.api.competition.exception.CompetitionEventNotFoundException;
import pl.tfhub.api.competition.exception.CompetitionResultNotFoundException;
import pl.tfhub.api.competition.repository.CompetitionEventRepository;
import pl.tfhub.api.competition.repository.CompetitionResultRepository;

@Service
public class CompetitionResultService {

	private final AthleteService athleteService;
	private final CompetitionEventRepository eventRepository;
	private final CompetitionResultRepository resultRepository;
	private final AccessScopeService accessScopeService;

	public CompetitionResultService(
			AthleteService athleteService,
			CompetitionEventRepository eventRepository,
			CompetitionResultRepository resultRepository,
			AccessScopeService accessScopeService) {
		this.athleteService = athleteService;
		this.eventRepository = eventRepository;
		this.resultRepository = resultRepository;
		this.accessScopeService = accessScopeService;
	}

	@Transactional(readOnly = true)
	public List<CompetitionResultResponse> getAthleteResults(long athleteId) {
		athleteService.getAthlete(athleteId);
		return resultRepository.findProfileResults(athleteId).stream()
				.map(CompetitionResultResponse::from)
				.toList();
	}

	@Transactional(readOnly = true)
	public CompetitionResultResponse getResult(long id) {
		CompetitionResult result = findResult(id);
		athleteService.getAthlete(result.getAthleteId());
		return CompetitionResultResponse.from(result);
	}

	@Transactional
	public CompetitionResultResponse createResult(long athleteId, SaveCompetitionResultRequest request) {
		accessScopeService.requireAdmin();
		var athlete = athleteService.getAthlete(athleteId);
		CompetitionEvent event = findEvent(request.eventId());
		if (!event.isActive()) {
			throw new CompetitionEventNotFoundException(event.getId());
		}
		CompetitionResult result = new CompetitionResult(
				athlete.id(),
				event,
				request.resultDate(),
				request.originalMark(),
				request.numericValue(),
				event.getUnit(),
				request.wind(),
				request.season(),
				request.seasonType(),
				request.competitionName(),
				request.location(),
				request.resultRound(),
				CompetitionResultSource.MANUAL,
				null,
				null);
		return CompetitionResultResponse.from(resultRepository.saveAndFlush(result));
	}

	@Transactional
	public CompetitionResultResponse updateResult(long id, SaveCompetitionResultRequest request) {
		accessScopeService.requireAdmin();
		CompetitionResult result = findResult(id);
		CompetitionEvent event = findEvent(request.eventId());
		if (!event.isActive() && !event.getId().equals(result.getEvent().getId())) {
			throw new CompetitionEventNotFoundException(event.getId());
		}
		result.update(
				event,
				request.resultDate(),
				request.originalMark(),
				request.numericValue(),
				event.getUnit(),
				request.wind(),
				request.season(),
				request.seasonType(),
				request.competitionName(),
				request.location(),
				request.resultRound(),
				result.getSource(),
				result.getSourceRecordKey(),
				result.getSourceFingerprint());
		return CompetitionResultResponse.from(resultRepository.saveAndFlush(result));
	}

	@Transactional
	public void deleteResult(long id) {
		accessScopeService.requireAdmin();
		resultRepository.delete(findResult(id));
	}

	private CompetitionEvent findEvent(long id) {
		return eventRepository.findById(id).orElseThrow(() -> new CompetitionEventNotFoundException(id));
	}

	private CompetitionResult findResult(long id) {
		return resultRepository.findById(id).orElseThrow(() -> new CompetitionResultNotFoundException(id));
	}
}

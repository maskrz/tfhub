package pl.tfhub.api.trainingimport.service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.tfhub.api.athlete.entity.Athlete;
import pl.tfhub.api.athlete.repository.AthleteRepository;
import pl.tfhub.api.trainingimport.dto.response.TrainingImportStatusResponse;
import pl.tfhub.api.trainingimport.entity.TrainingImport;
import pl.tfhub.api.trainingimport.entity.TrainingImportAttempt;
import pl.tfhub.api.trainingimport.entity.TrainingImportDraft;
import pl.tfhub.api.trainingimport.entity.TrainingImportDraftAthleteRow;
import pl.tfhub.api.trainingimport.entity.TrainingImportDraftDefinition;
import pl.tfhub.api.trainingimport.entity.TrainingImportDraftExecution;
import pl.tfhub.api.trainingimport.entity.TrainingImportDraftSegment;
import pl.tfhub.api.trainingimport.entity.TrainingImportSourceImage;
import pl.tfhub.api.trainingimport.enums.TrainingImportAttemptStatus;
import pl.tfhub.api.trainingimport.enums.TrainingImportAthleteMatchStatus;
import pl.tfhub.api.trainingimport.enums.TrainingImportStatus;
import pl.tfhub.api.trainingimport.config.TrainingImportProperties;
import pl.tfhub.api.trainingimport.exception.TrainingImportNotRetryableException;
import pl.tfhub.api.trainingimport.repository.TrainingImportAttemptRepository;
import pl.tfhub.api.trainingimport.repository.TrainingImportDraftAthleteRowRepository;
import pl.tfhub.api.trainingimport.repository.TrainingImportDraftDefinitionRepository;
import pl.tfhub.api.trainingimport.repository.TrainingImportDraftExecutionRepository;
import pl.tfhub.api.trainingimport.repository.TrainingImportDraftRepository;
import pl.tfhub.api.trainingimport.repository.TrainingImportDraftSegmentRepository;
import pl.tfhub.api.trainingimport.repository.TrainingImportRepository;
import pl.tfhub.api.trainingimport.repository.TrainingImportSourceImageRepository;
import pl.tfhub.api.user.entity.User;
import pl.tfhub.api.user.service.AccountManagementService;
import pl.tfhub.api.user.service.AuthenticatedUser;
import pl.tfhub.api.common.authorization.AccessScopeService;

@Service
public class TrainingImportProcessingStateService {

	private final TrainingImportAccessService accessService;
	private final TrainingImportRepository importRepository;
	private final TrainingImportAttemptRepository attemptRepository;
	private final TrainingImportSourceImageRepository sourceImageRepository;
	private final TrainingImportDraftRepository draftRepository;
	private final TrainingImportDraftDefinitionRepository definitionRepository;
	private final TrainingImportDraftSegmentRepository segmentRepository;
	private final TrainingImportDraftAthleteRowRepository athleteRowRepository;
	private final TrainingImportDraftExecutionRepository executionRepository;
	private final AthleteRepository athleteRepository;
	private final AccountManagementService accountManagementService;
	private final AccessScopeService accessScopeService;
	private final TrainingImportImageStorage imageStorage;
	private final TrainingImportProperties properties;

	@PersistenceContext
	private EntityManager entityManager;

	public TrainingImportProcessingStateService(
			TrainingImportAccessService accessService,
			TrainingImportRepository importRepository,
			TrainingImportAttemptRepository attemptRepository,
			TrainingImportSourceImageRepository sourceImageRepository,
			TrainingImportDraftRepository draftRepository,
			TrainingImportDraftDefinitionRepository definitionRepository,
			TrainingImportDraftSegmentRepository segmentRepository,
			TrainingImportDraftAthleteRowRepository athleteRowRepository,
			TrainingImportDraftExecutionRepository executionRepository,
			AthleteRepository athleteRepository,
			AccountManagementService accountManagementService,
			AccessScopeService accessScopeService,
			TrainingImportImageStorage imageStorage,
			TrainingImportProperties properties) {
		this.accessService = accessService;
		this.importRepository = importRepository;
		this.attemptRepository = attemptRepository;
		this.sourceImageRepository = sourceImageRepository;
		this.draftRepository = draftRepository;
		this.definitionRepository = definitionRepository;
		this.segmentRepository = segmentRepository;
		this.athleteRowRepository = athleteRowRepository;
		this.executionRepository = executionRepository;
		this.athleteRepository = athleteRepository;
		this.accountManagementService = accountManagementService;
		this.accessScopeService = accessScopeService;
		this.imageStorage = imageStorage;
		this.properties = properties;
	}

	@Transactional(readOnly = true)
	public TrainingImportStatusResponse getStatus(long importId) {
		TrainingImport trainingImport = accessService.getAccessibleImport(importId);
		return toStatusResponse(trainingImport);
	}

	@Transactional
	public Long claimPending(long importId) {
		TrainingImport trainingImport = importRepository.findByIdForUpdate(importId).orElse(null);
		if (trainingImport == null || trainingImport.getStatus() != TrainingImportStatus.PENDING) {
			return null;
		}
		return startAttempt(trainingImport, null).getId();
	}

	@Transactional
	public Long claimRetry(long importId) {
		TrainingImport trainingImport = accessService.getAccessibleImportForUpdate(importId);
		if (trainingImport.getStatus() != TrainingImportStatus.FAILED) {
			throw new TrainingImportNotRetryableException();
		}
		AuthenticatedUser currentUser = accessScopeService.currentUser();
		User requester = accountManagementService.getAccountEntity(currentUser.id());
		return startAttempt(trainingImport, requester).getId();
	}

	@Transactional(readOnly = true)
	public Long findNextPendingImportId() {
		List<Long> ids = importRepository.findIdsByStatusOrderByCreatedAt(
				TrainingImportStatus.PENDING, PageRequest.of(0, 1));
		return ids.isEmpty() ? null : ids.getFirst();
	}

	@Transactional(readOnly = true)
	public ProcessingInput loadProcessingInput(long attemptId) {
		TrainingImportAttempt attempt = attemptRepository.findById(attemptId).orElse(null);
		if (attempt == null || attempt.getStatus() != TrainingImportAttemptStatus.PROCESSING) {
			return null;
		}
		TrainingImport trainingImport = attempt.getTrainingImport();
		if (trainingImport.getStatus() != TrainingImportStatus.PROCESSING) {
			return null;
		}
		TrainingImportSourceImage sourceImage = sourceImageRepository.findById(trainingImport.getId())
				.orElseThrow(() -> new TrainingImportProcessingException(
						"SOURCE_IMAGE_UNAVAILABLE", "The training import source image is unavailable"));
		try {
			return new ProcessingInput(
					attemptId,
					trainingImport.getId(),
					trainingImport.getTrainingGroup().getId(),
					imageStorage.load(sourceImage.getStorageKey()));
		} catch (RuntimeException exception) {
			throw new TrainingImportProcessingException(
					"SOURCE_IMAGE_UNAVAILABLE", "The training import source image is unavailable");
		}
	}

	@Transactional
	public void complete(
			long attemptId,
			String rawModelResponse,
			TrainingImportRecognizedDraft recognizedDraft) {
		LockedAttempt locked = lockAttempt(attemptId);
		if (locked == null) {
			return;
		}
		TrainingImportAttempt attempt = locked.attempt();
		TrainingImport trainingImport = locked.trainingImport();
		if (!isCurrentProcessingAttempt(trainingImport, attempt)) {
			return;
		}

		TrainingImportDraft draft = draftRepository.findByTrainingImport_Id(trainingImport.getId())
				.orElse(null);
		if (draft == null) {
			draft = new TrainingImportDraft(
					trainingImport,
					attempt,
					recognizedDraft.name(),
					recognizedDraft.trainingDate(),
					recognizedDraft.recognitionMetadata());
			draft = draftRepository.saveAndFlush(draft);
		} else {
			deleteDraftChildren(draft);
			draft.replaceGeneratedContent(
					attempt,
					recognizedDraft.name(),
					recognizedDraft.trainingDate(),
					recognizedDraft.recognitionMetadata());
			draft = draftRepository.saveAndFlush(draft);
		}

		persistDraftChildren(draft, trainingImport, recognizedDraft);
		attempt.markSucceeded(now(), rawModelResponse);
		trainingImport.changeStatus(TrainingImportStatus.REVIEW_REQUIRED);
		attemptRepository.saveAndFlush(attempt);
		importRepository.saveAndFlush(trainingImport);
	}

	@Transactional
	public void fail(long attemptId, String rawModelResponse, String failureCode, String safeMessage) {
		LockedAttempt locked = lockAttempt(attemptId);
		if (locked == null) {
			return;
		}
		TrainingImportAttempt attempt = locked.attempt();
		TrainingImport trainingImport = locked.trainingImport();
		if (!isCurrentProcessingAttempt(trainingImport, attempt)) {
			return;
		}
		attempt.markFailed(now(), rawModelResponse, failureCode, safeMessage);
		trainingImport.changeStatus(TrainingImportStatus.FAILED);
		attemptRepository.saveAndFlush(attempt);
		importRepository.saveAndFlush(trainingImport);
	}

	@Transactional
	public void failStaleAttempts() {
		OffsetDateTime cutoff = now().minusNanos(
				java.time.Duration.ofMillis(properties.getProcessingStaleTimeoutMs()).toNanos());
		List<TrainingImportAttempt> staleAttempts = attemptRepository.findByStatusAndStartedAtBefore(
				TrainingImportAttemptStatus.PROCESSING, cutoff);
		for (TrainingImportAttempt staleAttempt : staleAttempts) {
			LockedAttempt locked = lockAttempt(staleAttempt.getId());
			if (locked == null) {
				continue;
			}
			TrainingImport trainingImport = locked.trainingImport();
			TrainingImportAttempt attempt = locked.attempt();
			if (isCurrentProcessingAttempt(trainingImport, attempt)
					&& attempt.getStartedAt().isBefore(cutoff)) {
				attempt.markFailed(
						now(),
						attempt.getRawModelResponse(),
						"PROCESSING_TIMEOUT",
						"Training import processing did not finish in time");
				trainingImport.changeStatus(TrainingImportStatus.FAILED);
				attemptRepository.saveAndFlush(attempt);
				importRepository.saveAndFlush(trainingImport);
			}
		}
	}

	private LockedAttempt lockAttempt(long attemptId) {
		TrainingImportAttempt attempt = attemptRepository.findById(attemptId).orElse(null);
		if (attempt == null) {
			return null;
		}
		TrainingImport trainingImport = importRepository
				.findByIdForUpdate(attempt.getTrainingImport().getId())
				.orElse(null);
		if (trainingImport == null) {
			return null;
		}
		entityManager.refresh(attempt);
		return new LockedAttempt(trainingImport, attempt);
	}

	private TrainingImportAttempt startAttempt(TrainingImport trainingImport, User requestedBy) {
		int attemptNumber = attemptRepository.findMaximumAttemptNumber(trainingImport.getId()) + 1;
		String modelName = properties.getGeminiModel() == null || properties.getGeminiModel().isBlank()
				? "UNCONFIGURED"
				: properties.getGeminiModel().strip();
		TrainingImportAttempt attempt = new TrainingImportAttempt(
				trainingImport,
				attemptNumber,
				requestedBy,
				modelName,
				TrainingImportAttemptStatus.PROCESSING,
				now(),
				null,
				null,
				null,
				null);
		trainingImport.changeStatus(TrainingImportStatus.PROCESSING);
		importRepository.saveAndFlush(trainingImport);
		return attemptRepository.saveAndFlush(attempt);
	}

	private TrainingImportStatusResponse toStatusResponse(TrainingImport trainingImport) {
		TrainingImportStatusResponse.Attempt latestAttempt = attemptRepository
				.findFirstByTrainingImport_IdOrderByAttemptNumberDesc(trainingImport.getId())
				.map(attempt -> new TrainingImportStatusResponse.Attempt(
						attempt.getAttemptNumber(),
						attempt.getStatus(),
						attempt.getStartedAt(),
						attempt.getFinishedAt(),
						attempt.getFailureCode(),
						attempt.getFailureMessage()))
				.orElse(null);
		return new TrainingImportStatusResponse(
				trainingImport.getId(),
				trainingImport.getTrainingGroup().getId(),
				trainingImport.getStatus(),
				trainingImport.getCreatedAt(),
				trainingImport.isPossibleDuplicate(),
				latestAttempt);
	}

	private boolean isCurrentProcessingAttempt(
			TrainingImport trainingImport,
			TrainingImportAttempt attempt) {
		return trainingImport != null
				&& attempt != null
				&& trainingImport.getStatus() == TrainingImportStatus.PROCESSING
				&& attempt.getStatus() == TrainingImportAttemptStatus.PROCESSING;
	}

	private void deleteDraftChildren(TrainingImportDraft draft) {
		List<TrainingImportDraftDefinition> definitions =
				definitionRepository.findByDraft_IdOrderBySequenceNumberAsc(draft.getId());
		List<TrainingImportDraftSegment> segments = new ArrayList<>();
		List<TrainingImportDraftAthleteRow> athleteRows = new ArrayList<>();
		for (TrainingImportDraftDefinition definition : definitions) {
			segments.addAll(segmentRepository.findByDefinition_IdOrderBySequenceNumberAsc(definition.getId()));
			athleteRows.addAll(athleteRowRepository.findByDefinition_IdOrderByIdAsc(definition.getId()));
		}
		List<TrainingImportDraftExecution> executions =
				executionRepository.findByDraft_IdOrderByIdAsc(draft.getId());
		executionRepository.deleteAll(executions);
		executionRepository.flush();
		athleteRowRepository.deleteAll(athleteRows);
		athleteRowRepository.flush();
		segmentRepository.deleteAll(segments);
		segmentRepository.flush();
		definitionRepository.deleteAll(definitions);
		definitionRepository.flush();
	}

	private void persistDraftChildren(
			TrainingImportDraft draft,
			TrainingImport trainingImport,
			TrainingImportRecognizedDraft recognizedDraft) {
		List<TrainingImportDraftDefinition> definitions = new ArrayList<>();
		Map<Integer, TrainingImportDraftDefinition> definitionsBySequence = new HashMap<>();
		for (TrainingImportRecognizedDraft.Definition definitionData : recognizedDraft.definitions()) {
			TrainingImportDraftDefinition definition = new TrainingImportDraftDefinition(
					draft, definitionData.name(), definitionData.sequenceNumber());
			definitions.add(definition);
			definitionsBySequence.put(definitionData.sequenceNumber(), definition);
		}
		definitionRepository.saveAllAndFlush(definitions);

		List<TrainingImportDraftSegment> segments = new ArrayList<>();
		Map<SequenceKey, TrainingImportDraftSegment> segmentsBySequence = new HashMap<>();
		for (TrainingImportRecognizedDraft.Definition definitionData : recognizedDraft.definitions()) {
			TrainingImportDraftDefinition definition = definitionsBySequence.get(definitionData.sequenceNumber());
			for (TrainingImportRecognizedDraft.Segment segmentData : definitionData.segments()) {
				TrainingImportDraftSegment segment = new TrainingImportDraftSegment(
						draft,
						definition,
						segmentData.sequenceNumber(),
						segmentData.distanceM(),
						segmentData.restAfterSeconds(),
						segmentData.recognitionMetadata());
				segments.add(segment);
			}
		}
		segmentRepository.saveAllAndFlush(segments);
		for (TrainingImportDraftSegment segment : segments) {
			segmentsBySequence.put(
					new SequenceKey(segment.getDefinition().getSequenceNumber(), segment.getSequenceNumber()),
					segment);
		}

		List<TrainingImportDraftAthleteRow> rows = new ArrayList<>();
		Map<DefinitionRowKey, TrainingImportDraftAthleteRow> rowsByKey = new HashMap<>();
		Set<Long> matchedAthleteIds = new HashSet<>();
		for (TrainingImportRecognizedDraft.Definition definitionData : recognizedDraft.definitions()) {
			TrainingImportDraftDefinition definition = definitionsBySequence.get(definitionData.sequenceNumber());
			for (int rowIndex = 0; rowIndex < definitionData.athleteRows().size(); rowIndex++) {
				TrainingImportRecognizedDraft.AthleteRow rowData = definitionData.athleteRows().get(rowIndex);
				Athlete athlete = null;
				TrainingImportAthleteMatchStatus matchStatus = rowData.matchStatus();
				Map<String, Object> metadata = new LinkedHashMap<>(rowData.recognitionMetadata());
				if (matchStatus == TrainingImportAthleteMatchStatus.MATCHED) {
					athlete = athleteRepository.findByIdAndTrainingGroup_Id(
									rowData.athleteId(), trainingImport.getTrainingGroup().getId())
							.orElse(null);
					if (athlete == null || !matchedAthleteIds.add(athlete.getId())) {
						athlete = null;
						matchStatus = TrainingImportAthleteMatchStatus.UNRESOLVED;
						metadata.put("uncertain", true);
						metadata.put("matchingReason", "Athlete no longer belongs to the selected group");
					}
				}
				TrainingImportDraftAthleteRow row = new TrainingImportDraftAthleteRow(
						draft,
						definition,
						athlete,
						rowData.recognizedName(),
						matchStatus,
						Map.copyOf(metadata));
				rows.add(row);
				rowsByKey.put(new DefinitionRowKey(definitionData.sequenceNumber(), rowIndex), row);
			}
		}
		athleteRowRepository.saveAllAndFlush(rows);

		List<TrainingImportDraftExecution> executions = new ArrayList<>();
		for (TrainingImportRecognizedDraft.Definition definitionData : recognizedDraft.definitions()) {
			TrainingImportDraftDefinition definition = definitionsBySequence.get(definitionData.sequenceNumber());
			for (int rowIndex = 0; rowIndex < definitionData.athleteRows().size(); rowIndex++) {
				TrainingImportRecognizedDraft.AthleteRow rowData = definitionData.athleteRows().get(rowIndex);
				TrainingImportDraftAthleteRow row =
						rowsByKey.get(new DefinitionRowKey(definitionData.sequenceNumber(), rowIndex));
				for (TrainingImportRecognizedDraft.Execution executionData : rowData.executions()) {
					TrainingImportDraftSegment segment = segmentsBySequence.get(
							new SequenceKey(definitionData.sequenceNumber(),
									executionData.segmentSequenceNumber()));
					executions.add(new TrainingImportDraftExecution(
							draft,
							definition,
							row,
							segment,
							executionData.status(),
							executionData.measuredResult(),
							executionData.usesSpikes(),
							executionData.recognizedText(),
							executionData.recognitionMetadata()));
				}
			}
		}
		executionRepository.saveAllAndFlush(executions);
	}

	private OffsetDateTime now() {
		return OffsetDateTime.now(ZoneOffset.UTC);
	}

	public record ProcessingInput(long attemptId, long importId, long trainingGroupId, byte[] image) {
	}

	private record SequenceKey(int definitionSequence, int segmentSequence) {
	}

	private record DefinitionRowKey(int definitionSequence, int rowIndex) {
	}

	private record LockedAttempt(TrainingImport trainingImport, TrainingImportAttempt attempt) {
	}
}

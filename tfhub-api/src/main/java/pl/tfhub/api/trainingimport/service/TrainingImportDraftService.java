package pl.tfhub.api.trainingimport.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.tfhub.api.athlete.entity.Athlete;
import pl.tfhub.api.athlete.repository.AthleteRepository;
import pl.tfhub.api.training.enums.TrainingExecutionStatus;
import pl.tfhub.api.traininggroup.dto.response.TrainingGroupResponse;
import pl.tfhub.api.trainingimport.dto.request.TrainingImportDraftUpdateRequest;
import pl.tfhub.api.trainingimport.dto.response.TrainingImportAthleteCandidateResponse;
import pl.tfhub.api.trainingimport.dto.response.TrainingImportDraftResponse;
import pl.tfhub.api.trainingimport.entity.TrainingImport;
import pl.tfhub.api.trainingimport.entity.TrainingImportDraft;
import pl.tfhub.api.trainingimport.entity.TrainingImportDraftAthleteRow;
import pl.tfhub.api.trainingimport.entity.TrainingImportDraftDefinition;
import pl.tfhub.api.trainingimport.entity.TrainingImportDraftExecution;
import pl.tfhub.api.trainingimport.entity.TrainingImportDraftSegment;
import pl.tfhub.api.trainingimport.enums.TrainingImportAthleteMatchStatus;
import pl.tfhub.api.trainingimport.enums.TrainingImportStatus;
import pl.tfhub.api.trainingimport.exception.InvalidTrainingImportDraftException;
import pl.tfhub.api.trainingimport.exception.TrainingImportDraftNotFoundException;
import pl.tfhub.api.trainingimport.exception.TrainingImportNotReviewableException;
import pl.tfhub.api.trainingimport.repository.TrainingImportDraftAthleteRowRepository;
import pl.tfhub.api.trainingimport.repository.TrainingImportDraftDefinitionRepository;
import pl.tfhub.api.trainingimport.repository.TrainingImportDraftExecutionRepository;
import pl.tfhub.api.trainingimport.repository.TrainingImportDraftRepository;
import pl.tfhub.api.trainingimport.repository.TrainingImportDraftSegmentRepository;

@Service
public class TrainingImportDraftService {

	private static final int MAX_CANDIDATE_QUERY_LENGTH = 100;

	private final TrainingImportAccessService accessService;
	private final TrainingImportDraftRepository draftRepository;
	private final TrainingImportDraftDefinitionRepository definitionRepository;
	private final TrainingImportDraftSegmentRepository segmentRepository;
	private final TrainingImportDraftAthleteRowRepository athleteRowRepository;
	private final TrainingImportDraftExecutionRepository executionRepository;
	private final AthleteRepository athleteRepository;

	public TrainingImportDraftService(
			TrainingImportAccessService accessService,
			TrainingImportDraftRepository draftRepository,
			TrainingImportDraftDefinitionRepository definitionRepository,
			TrainingImportDraftSegmentRepository segmentRepository,
			TrainingImportDraftAthleteRowRepository athleteRowRepository,
			TrainingImportDraftExecutionRepository executionRepository,
			AthleteRepository athleteRepository) {
		this.accessService = accessService;
		this.draftRepository = draftRepository;
		this.definitionRepository = definitionRepository;
		this.segmentRepository = segmentRepository;
		this.athleteRowRepository = athleteRowRepository;
		this.executionRepository = executionRepository;
		this.athleteRepository = athleteRepository;
	}

	@Transactional(readOnly = true)
	public TrainingImportDraftResponse getDraft(long importId) {
		TrainingImport trainingImport = getReadableImport(importId);
		TrainingImportDraft draft = findDraft(importId);
		return toResponse(draft, loadDraftData(draft), trainingImport);
	}

	@Transactional
	public TrainingImportDraftResponse updateDraft(long importId, TrainingImportDraftUpdateRequest request) {
		TrainingImport trainingImport = getReviewableImportForUpdate(importId);
		TrainingImportDraft draft = findDraft(importId);
		DraftData existing = loadDraftData(draft);
		Map<Long, Athlete> matchedAthletes = validateUpdate(request, existing);

		executionRepository.deleteAll(existing.executions());
		executionRepository.flush();
		athleteRowRepository.deleteAll(existing.athleteRows());
		athleteRowRepository.flush();
		segmentRepository.deleteAll(existing.segments());
		segmentRepository.flush();
		definitionRepository.deleteAll(existing.definitions());
		definitionRepository.flush();

		draft.update(request.name(), request.trainingDate());
		draft.setRecognitionMetadata(TrainingImportRecognitionMetadata.withReviewState(
				draft.getRecognitionMetadata(), request.recognitionReviewed()));
		draftRepository.saveAndFlush(draft);

		List<TrainingImportDraftDefinition> definitions = new ArrayList<>();
		for (TrainingImportDraftUpdateRequest.Definition definitionRequest : request.definitions()) {
			TrainingImportDraftDefinition definition = new TrainingImportDraftDefinition(
					draft, definitionRequest.name(), definitionRequest.sequenceNumber());
			definitions.add(definition);
		}
		definitions = definitionRepository.saveAllAndFlush(definitions);

		List<TrainingImportDraftSegment> segments = new ArrayList<>();
		Map<SequenceKey, TrainingImportDraftSegment> segmentsBySequence = new HashMap<>();
		for (int definitionIndex = 0; definitionIndex < request.definitions().size(); definitionIndex++) {
			TrainingImportDraftUpdateRequest.Definition definitionRequest =
					request.definitions().get(definitionIndex);
			TrainingImportDraftDefinition definition = definitions.get(definitionIndex);
			for (TrainingImportDraftUpdateRequest.Segment segmentRequest : definitionRequest.segments()) {
				Map<String, Object> metadata = metadataForSegment(segmentRequest, existing);
				TrainingImportDraftSegment segment = new TrainingImportDraftSegment(
						draft,
						definition,
						segmentRequest.sequenceNumber(),
						segmentRequest.distanceM(),
						segmentRequest.restAfterSeconds(),
						TrainingImportRecognitionMetadata.withReviewState(
								metadata, segmentRequest.recognitionReviewed()));
				segments.add(segment);
			}
		}
		segments = segmentRepository.saveAllAndFlush(segments);
		for (int definitionIndex = 0; definitionIndex < request.definitions().size(); definitionIndex++) {
			TrainingImportDraftUpdateRequest.Definition definitionRequest =
					request.definitions().get(definitionIndex);
			TrainingImportDraftDefinition definition = definitions.get(definitionIndex);
			List<TrainingImportDraftSegment> savedSegments = segments.stream()
					.filter(segment -> segment.getDefinition().getId().equals(definition.getId()))
					.toList();
			for (int segmentIndex = 0; segmentIndex < definitionRequest.segments().size(); segmentIndex++) {
				TrainingImportDraftUpdateRequest.Segment segmentRequest =
						definitionRequest.segments().get(segmentIndex);
				TrainingImportDraftSegment savedSegment = savedSegments.get(segmentIndex);
				segmentsBySequence.put(
						new SequenceKey(definition.getId(), segmentRequest.sequenceNumber()), savedSegment);
			}
		}

		List<TrainingImportDraftAthleteRow> athleteRows = new ArrayList<>();
		for (int definitionIndex = 0; definitionIndex < request.definitions().size(); definitionIndex++) {
			TrainingImportDraftUpdateRequest.Definition definitionRequest =
					request.definitions().get(definitionIndex);
			TrainingImportDraftDefinition definition = definitions.get(definitionIndex);
			for (TrainingImportDraftUpdateRequest.AthleteRow rowRequest : definitionRequest.athleteRows()) {
				Athlete athlete = rowRequest.matchStatus() == TrainingImportAthleteMatchStatus.MATCHED
						? matchedAthletes.get(rowRequest.athleteId())
						: null;
				TrainingImportDraftAthleteRow previous = rowRequest.id() == null
						? null
						: existing.athleteRowsById().get(rowRequest.id());
				String recognizedName = previous == null ? rowRequest.recognizedName() : previous.getRecognizedName();
				Map<String, Object> metadata = previous == null
						? Map.of()
						: previous.getRecognitionMetadata();
				TrainingImportDraftAthleteRow row = new TrainingImportDraftAthleteRow(
						draft,
						definition,
						athlete,
						recognizedName,
						rowRequest.matchStatus(),
						TrainingImportRecognitionMetadata.withReviewState(metadata, true));
				athleteRows.add(row);
			}
		}
		athleteRows = athleteRowRepository.saveAllAndFlush(athleteRows);
		List<TrainingImportDraftExecution> executions = new ArrayList<>();
		for (int definitionIndex = 0; definitionIndex < request.definitions().size(); definitionIndex++) {
			TrainingImportDraftUpdateRequest.Definition definitionRequest =
					request.definitions().get(definitionIndex);
			TrainingImportDraftDefinition definition = definitions.get(definitionIndex);
			List<TrainingImportDraftAthleteRow> savedRows = athleteRows.stream()
					.filter(row -> row.getDefinition().getId().equals(definition.getId()))
					.toList();
			for (int rowIndex = 0; rowIndex < definitionRequest.athleteRows().size(); rowIndex++) {
				TrainingImportDraftUpdateRequest.AthleteRow rowRequest = definitionRequest.athleteRows().get(rowIndex);
				TrainingImportDraftAthleteRow row = savedRows.get(rowIndex);
				for (TrainingImportDraftUpdateRequest.Execution executionRequest : rowRequest.executions()) {
					TrainingImportDraftSegment segment = segmentsBySequence.get(
							new SequenceKey(definition.getId(), executionRequest.segmentSequenceNumber()));
					TrainingImportDraftExecution previous = executionRequest.id() == null
							? null
							: existing.executionsById().get(executionRequest.id());
					Map<String, Object> metadata = previous == null
							? Map.of()
							: previous.getRecognitionMetadata();
					String recognizedText = previous == null ? null : previous.getRecognizedText();
					TrainingImportDraftExecution execution = new TrainingImportDraftExecution(
							draft,
							definition,
							row,
							segment,
							executionRequest.status(),
							executionRequest.measuredResult(),
							executionRequest.usesSpikes(),
							recognizedText,
							TrainingImportRecognitionMetadata.withReviewState(
									metadata, executionRequest.recognitionReviewed()));
					executions.add(execution);
				}
			}
		}
		executionRepository.saveAllAndFlush(executions);

		DraftData updated = loadDraftData(draft);
		TrainingImportDraftResponse response = toResponse(draft, updated, trainingImport);
		if (response.validation().issues().stream().anyMatch(issue ->
				!issue.code().equals("SEGMENT_DISTANCE_UNRESOLVED")
						&& !issue.code().equals("SEGMENT_DISTANCE_REVIEW_REQUIRED"))) {
			throw new InvalidTrainingImportDraftException(
					"Training import draft contains unresolved validation issues");
		}
		return response;
	}

	@Transactional(readOnly = true)
	public List<TrainingImportAthleteCandidateResponse> findAthleteCandidates(long importId, String query) {
		TrainingImport trainingImport = getReviewableImport(importId);
		String normalizedQuery = query == null ? null : query.strip().toLowerCase(Locale.ROOT);
		if (normalizedQuery != null && normalizedQuery.isEmpty()) {
			normalizedQuery = null;
		}
		if (normalizedQuery != null && normalizedQuery.length() > MAX_CANDIDATE_QUERY_LENGTH) {
			throw new InvalidTrainingImportDraftException("Athlete candidate query is too long");
		}
		long groupId = trainingImport.getTrainingGroup().getId();
		List<Athlete> candidates = normalizedQuery == null
				? athleteRepository.findAllCandidatesInTrainingGroup(groupId)
				: athleteRepository.findCandidatesInTrainingGroup(groupId, normalizedQuery);
		return candidates
				.stream()
				.map(TrainingImportAthleteCandidateResponse::from)
				.toList();
	}

	private TrainingImport getReviewableImport(long importId) {
		TrainingImport trainingImport = accessService.getAccessibleImport(importId);
		if (trainingImport.getStatus() != TrainingImportStatus.REVIEW_REQUIRED) {
			throw new TrainingImportNotReviewableException();
		}
		return trainingImport;
	}

	private TrainingImport getReviewableImportForUpdate(long importId) {
		TrainingImport trainingImport = accessService.getAccessibleImportForUpdate(importId);
		if (trainingImport.getStatus() != TrainingImportStatus.REVIEW_REQUIRED) {
			throw new TrainingImportNotReviewableException();
		}
		return trainingImport;
	}

	private TrainingImport getReadableImport(long importId) {
		TrainingImport trainingImport = accessService.getAccessibleImport(importId);
		if (trainingImport.getStatus() != TrainingImportStatus.REVIEW_REQUIRED
				&& trainingImport.getStatus() != TrainingImportStatus.ACCEPTED
				&& trainingImport.getStatus() != TrainingImportStatus.REJECTED) {
			throw new TrainingImportNotReviewableException();
		}
		return trainingImport;
	}

	private TrainingImportDraft findDraft(long importId) {
		return draftRepository.findByTrainingImport_Id(importId)
				.orElseThrow(() -> new TrainingImportDraftNotFoundException(importId));
	}

	private DraftData loadDraftData(TrainingImportDraft draft) {
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
		return new DraftData(draft, definitions, segments, athleteRows, executions);
	}

	private TrainingImportDraftResponse toResponse(
			TrainingImportDraft draft,
			DraftData data,
			TrainingImport trainingImport) {
		Set<Long> eligibleSuggestionIds = athleteRepository
				.findByTrainingGroup_IdOrderByLastNameAscFirstNameAscIdAsc(trainingImport.getTrainingGroup().getId())
				.stream().filter(Athlete::isActive).map(Athlete::getId).collect(java.util.stream.Collectors.toSet());
		Map<Long, List<TrainingImportDraftSegment>> segmentsByDefinition = new HashMap<>();
		for (TrainingImportDraftSegment segment : data.segments()) {
			segmentsByDefinition.computeIfAbsent(segment.getDefinition().getId(), ignored -> new ArrayList<>())
					.add(segment);
		}
		Map<Long, List<TrainingImportDraftAthleteRow>> rowsByDefinition = new HashMap<>();
		for (TrainingImportDraftAthleteRow row : data.athleteRows()) {
			rowsByDefinition.computeIfAbsent(row.getDefinition().getId(), ignored -> new ArrayList<>()).add(row);
		}
		Map<Long, List<TrainingImportDraftExecution>> executionsByRow = new HashMap<>();
		for (TrainingImportDraftExecution execution : data.executions()) {
			executionsByRow.computeIfAbsent(execution.getAthleteRow().getId(), ignored -> new ArrayList<>())
					.add(execution);
		}

		List<TrainingImportDraftResponse.Definition> definitionResponses = data.definitions().stream()
				.map(definition -> {
					List<TrainingImportDraftResponse.Segment> segmentResponses =
							segmentsByDefinition.getOrDefault(definition.getId(), List.of()).stream()
									.sorted((left, right) -> Integer.compare(
											left.getSequenceNumber(), right.getSequenceNumber()))
									.map(segment -> new TrainingImportDraftResponse.Segment(
											segment.getId(),
											segment.getSequenceNumber(),
											segment.getDistanceM(),
											segment.getRestAfterSeconds(),
											segment.getRecognitionMetadata(),
											TrainingImportRecognitionMetadata.isReviewed(
													segment.getRecognitionMetadata())))
									.toList();
					List<TrainingImportDraftResponse.AthleteRow> rowResponses =
							rowsByDefinition.getOrDefault(definition.getId(), List.of()).stream()
									.sorted((left, right) -> Long.compare(left.getId(), right.getId()))
									.map(row -> {
										List<TrainingImportDraftResponse.Execution> executionResponses =
												executionsByRow.getOrDefault(row.getId(), List.of()).stream()
														.sorted((left, right) -> Integer.compare(
																left.getSegment().getSequenceNumber(),
																right.getSegment().getSequenceNumber()))
														.map(execution -> new TrainingImportDraftResponse.Execution(
																execution.getId(),
																execution.getSegment().getId(),
																execution.getSegment().getSequenceNumber(),
																execution.getStatus(),
																execution.getMeasuredResult(),
																execution.getUsesSpikes(),
																execution.getRecognizedText(),
																execution.getRecognitionMetadata(),
																TrainingImportRecognitionMetadata.isReviewed(
																		execution.getRecognitionMetadata())))
														.toList();
										return new TrainingImportDraftResponse.AthleteRow(
												row.getId(),
												row.getRecognizedName(),
												row.getMatchStatus(),
												row.getAthlete() == null
														? null
														: TrainingImportAthleteCandidateResponse.from(row.getAthlete()),
												filterSuggestions(row.getRecognitionMetadata(), eligibleSuggestionIds),
												executionResponses);
									})
									.toList();
					return new TrainingImportDraftResponse.Definition(
							definition.getId(),
							definition.getName(),
							definition.getSequenceNumber(),
							segmentResponses,
							rowResponses);
				})
				.toList();

		List<TrainingImportDraftResponse.ValidationIssue> issues =
				validateReadiness(draft, data, trainingImport.getTrainingGroup().getId());
		return new TrainingImportDraftResponse(
				trainingImport.getId(),
				trainingImport.getStatus(),
				new TrainingGroupResponse(
						trainingImport.getTrainingGroup().getId(),
						trainingImport.getTrainingGroup().getName()),
				draft.getName(),
				draft.getTrainingDate(),
				draft.getRecognitionMetadata(),
				TrainingImportRecognitionMetadata.isReviewed(draft.getRecognitionMetadata()),
				definitionResponses,
				new TrainingImportDraftResponse.Validation(issues.isEmpty(), issues));
	}

	private Map<String, Object> filterSuggestions(Map<String, Object> metadata, Set<Long> eligibleIds) {
		if (!(metadata.get("suggestedAthleteIds") instanceof List<?> suggestions)) {
			return metadata;
		}
		Map<String, Object> filtered = new HashMap<>(metadata);
		filtered.put("suggestedAthleteIds", suggestions.stream()
				.filter(Number.class::isInstance)
				.map(Number.class::cast)
				.map(Number::longValue)
				.filter(eligibleIds::contains)
				.toList());
		return java.util.Collections.unmodifiableMap(filtered);
	}

	private List<TrainingImportDraftResponse.ValidationIssue> validateReadiness(
			TrainingImportDraft draft,
			DraftData data,
			long groupId) {
		List<TrainingImportDraftResponse.ValidationIssue> issues = new ArrayList<>();
		addRecognitionIssue(issues, "training", draft.getRecognitionMetadata(),
				TrainingImportRecognitionMetadata.isReviewed(draft.getRecognitionMetadata()));
		if (data.definitions().isEmpty()) {
			issues.add(issue("NO_DEFINITIONS", "definitions", "At least one definition is required"));
		}
		Map<Long, Integer> segmentCounts = new HashMap<>();
		for (TrainingImportDraftSegment segment : data.segments()) {
			segmentCounts.merge(segment.getDefinition().getId(), 1, Integer::sum);
			String path = "definitions/" + segment.getDefinition().getId()
					+ "/segments/" + segment.getSequenceNumber();
			if (segment.getDistanceM() == null || segment.getDistanceM() <= 0) {
				issues.add(issue("SEGMENT_DISTANCE_UNRESOLVED", path + "/distanceM",
						"Definition " + segment.getDefinition().getSequenceNumber() + ", segment "
								+ segment.getSequenceNumber() + " requires a positive distance"));
			}
			if (TrainingImportRecognitionMetadata.isUncertain(segment.getRecognitionMetadata())
					&& !TrainingImportRecognitionMetadata.isReviewed(segment.getRecognitionMetadata())) {
				issues.add(issue(segment.getDistanceM() == null
								? "SEGMENT_DISTANCE_REVIEW_REQUIRED" : "RECOGNITION_REVIEW_REQUIRED",
						path, "Uncertain recognized values must be confirmed or corrected"));
			}
		}
		for (TrainingImportDraftDefinition definition : data.definitions()) {
			if (segmentCounts.getOrDefault(definition.getId(), 0) == 0) {
				issues.add(issue("NO_SEGMENTS", "definitions/" + definition.getId() + "/segments",
						"Every definition must contain at least one segment"));
			}
		}
		Map<Long, Set<Long>> executionsByRow = new HashMap<>();
		for (TrainingImportDraftExecution execution : data.executions()) {
			executionsByRow.computeIfAbsent(execution.getAthleteRow().getId(), ignored -> new HashSet<>())
					.add(execution.getSegment().getId());
			addRecognitionIssue(issues, "athleteRows/" + execution.getAthleteRow().getId()
					+ "/executions/" + execution.getSegment().getSequenceNumber(),
					execution.getRecognitionMetadata(),
					TrainingImportRecognitionMetadata.isReviewed(execution.getRecognitionMetadata()));
			if (execution.getUsesSpikes() == null) {
				issues.add(issue("SPIKE_USAGE_UNRESOLVED",
						"athleteRows/" + execution.getAthleteRow().getId()
								+ "/executions/" + execution.getSegment().getSequenceNumber(),
						"Spike usage must be confirmed"));
			}
		}
		for (TrainingImportDraftAthleteRow row : data.athleteRows()) {
			if (row.getMatchStatus() == TrainingImportAthleteMatchStatus.UNRESOLVED) {
				issues.add(issue("ATHLETE_UNRESOLVED", "athleteRows/" + row.getId(),
						"Every athlete row must be matched or skipped"));
			}
			if (row.getMatchStatus() == TrainingImportAthleteMatchStatus.MATCHED
					&& !athleteRepository.existsByIdAndTrainingGroup_Id(row.getAthlete().getId(), groupId)) {
				issues.add(issue("ATHLETE_OUTSIDE_GROUP", "athleteRows/" + row.getId(),
						"Matched athletes must belong to the selected training group"));
			}
			if (row.getMatchStatus() != TrainingImportAthleteMatchStatus.UNRESOLVED) {
				addRecognitionIssue(issues, "athleteRows/" + row.getId(), row.getRecognitionMetadata(), true);
			}
			int expectedExecutionCount = segmentCounts.getOrDefault(row.getDefinition().getId(), 0);
			if (executionsByRow.getOrDefault(row.getId(), Set.of()).size() != expectedExecutionCount) {
				issues.add(issue("EXECUTIONS_INCOMPLETE", "athleteRows/" + row.getId() + "/executions",
						"Every athlete row must have execution data for each segment in its definition"));
			}
		}
		return List.copyOf(issues);
	}

	private void addRecognitionIssue(
			List<TrainingImportDraftResponse.ValidationIssue> issues,
			String path,
			Map<String, Object> metadata,
			boolean reviewed) {
		if (TrainingImportRecognitionMetadata.isUncertain(metadata) && !reviewed) {
			issues.add(issue("RECOGNITION_REVIEW_REQUIRED", path,
					"Uncertain recognized values must be confirmed or corrected"));
		}
	}

	private TrainingImportDraftResponse.ValidationIssue issue(String code, String path, String message) {
		return new TrainingImportDraftResponse.ValidationIssue(code, path, message);
	}

	private Map<Long, Athlete> validateUpdate(
			TrainingImportDraftUpdateRequest request,
			DraftData existing) {
		if (request == null || request.name() == null || request.name().isBlank()
				|| request.name().length() > 255 || request.trainingDate() == null) {
			throw invalid("Training name and date are required");
		}
		if (request.definitions() == null || request.definitions().isEmpty()) {
			throw invalid("At least one training definition is required");
		}
		if (TrainingImportRecognitionMetadata.isUncertain(existingRootMetadata(existing))
				&& !request.recognitionReviewed()) {
			throw invalid("Uncertain training information must be confirmed or corrected");
		}

		Map<Long, TrainingImportDraftDefinition> definitionsById = existing.definitionsById();
		Map<Long, TrainingImportDraftSegment> segmentsById = existing.segmentsById();
		Map<Long, TrainingImportDraftAthleteRow> rowsById = existing.athleteRowsById();
		Set<Long> usedDefinitionIds = new HashSet<>();
		Set<Long> usedSegmentIds = new HashSet<>();
		Set<Long> usedRowIds = new HashSet<>();
		Set<Long> usedExecutionIds = new HashSet<>();
		Set<Integer> definitionSequences = new HashSet<>();
		Set<Long> matchedAthleteIds = new HashSet<>();
		Map<Long, Athlete> matchedAthletes = new HashMap<>();

		for (TrainingImportDraftUpdateRequest.Definition definition : request.definitions()) {
			if (definition == null || definition.name() == null || definition.name().isBlank()
					|| definition.name().length() > 255 || definition.sequenceNumber() <= 0
					|| !definitionSequences.add(definition.sequenceNumber())) {
				throw invalid("Definition sequence numbers must be positive and unique");
			}
			if (definition.id() != null) {
				TrainingImportDraftDefinition old = definitionsById.get(definition.id());
				if (old == null || !usedDefinitionIds.add(definition.id())) {
					throw invalid("A definition ID does not belong to this draft or is duplicated");
				}
			}
			if (definition.segments() == null || definition.segments().isEmpty()
					|| definition.athleteRows() == null) {
				throw invalid("Every definition must contain at least one segment");
			}
			Set<Integer> segmentSequences = new HashSet<>();
			for (TrainingImportDraftUpdateRequest.Segment segment : definition.segments()) {
				if (segment == null || segment.sequenceNumber() <= 0
						|| (segment.distanceM() != null && segment.distanceM() <= 0)
						|| (segment.restAfterSeconds() != null && segment.restAfterSeconds() < 0)
						|| !segmentSequences.add(segment.sequenceNumber())) {
					throw invalid("Segment values must be valid and sequence numbers unique within a definition");
				}
				if (segment.id() != null) {
					TrainingImportDraftSegment old = segmentsById.get(segment.id());
					if (old == null || !usedSegmentIds.add(segment.id())
							|| definition.id() == null
							|| !old.getDefinition().getId().equals(definition.id())) {
						throw invalid("A segment ID does not belong to its definition or is duplicated");
					}
					if (segment.distanceM() != null
							&& TrainingImportRecognitionMetadata.isUncertain(old.getRecognitionMetadata())
							&& !segment.recognitionReviewed()) {
						throw invalid("Uncertain segment information must be confirmed or corrected");
					}
				}
			}
		}

		for (TrainingImportDraftUpdateRequest.Definition definition : request.definitions()) {
			for (TrainingImportDraftUpdateRequest.AthleteRow row : definition.athleteRows()) {
				if (row == null) {
					throw invalid("Athlete rows must not be null");
				}
				TrainingImportDraftAthleteRow old = null;
				if (row.id() != null) {
					old = rowsById.get(row.id());
					if (old == null || !usedRowIds.add(row.id())) {
						throw invalid("An athlete row ID does not belong to this draft or is duplicated");
					}
					if (row.recognizedName() != null && !row.recognizedName().equals(old.getRecognizedName())) {
						throw invalid("Originally recognized athlete text cannot be changed");
					}
				} else if (row.recognizedName() == null || row.recognizedName().isBlank()) {
					throw invalid("A recognized name is required for a new athlete row");
				}
				if (row.matchStatus() == null
						|| row.matchStatus() == TrainingImportAthleteMatchStatus.UNRESOLVED) {
					throw invalid("Every athlete row must be matched or explicitly skipped");
				}
				if (row.matchStatus() == TrainingImportAthleteMatchStatus.MATCHED) {
					if (row.athleteId() == null || row.athleteId() <= 0) {
						throw invalid("A matched athlete row must include an athlete ID");
					}
					if (!matchedAthleteIds.add(row.athleteId())) {
						throw invalid("An athlete can only be matched once within an import");
					}
					Athlete athlete = athleteRepository.findByIdAndTrainingGroup_Id(
									row.athleteId(),
									existing.draft().getTrainingImport().getTrainingGroup().getId())
							.orElseThrow(() -> invalid("Matched athlete must belong to the selected training group"));
					matchedAthletes.put(athlete.getId(), athlete);
				} else if (row.athleteId() != null) {
					throw invalid("Skipped athlete rows cannot reference an athlete");
				}

				validateExecutions(row, definition, existing, usedExecutionIds);
			}
		}
		return matchedAthletes;
	}

	private void validateExecutions(
			TrainingImportDraftUpdateRequest.AthleteRow row,
			TrainingImportDraftUpdateRequest.Definition definition,
			DraftData existing,
			Set<Long> usedExecutionIds) {
		if (row.executions() == null || row.executions().size() != definition.segments().size()) {
			throw invalid("Every athlete row must include one execution for each segment");
		}
		Map<Integer, TrainingImportDraftUpdateRequest.Segment> segmentsBySequence = new HashMap<>();
		for (TrainingImportDraftUpdateRequest.Segment segment : definition.segments()) {
			segmentsBySequence.put(segment.sequenceNumber(), segment);
		}
		Set<Integer> executionSequences = new HashSet<>();
		for (TrainingImportDraftUpdateRequest.Execution execution : row.executions()) {
			if (execution == null) {
				throw invalid("Executions must not be null");
			}
			if (!executionSequences.add(execution.segmentSequenceNumber())) {
				throw invalid("An athlete row cannot contain duplicate execution segments");
			}
			TrainingImportDraftUpdateRequest.Segment targetSegment =
					segmentsBySequence.get(execution.segmentSequenceNumber());
			if (targetSegment == null) {
				throw invalid("Execution references a segment outside its definition");
			}
			if (execution.status() == null
					|| ((execution.status() == TrainingExecutionStatus.RECORDED)
							!= (execution.measuredResult() != null))) {
				throw invalid("A measured result is required only for RECORDED executions");
			}
			if (execution.measuredResult() != null
					&& (execution.measuredResult().signum() < 0
							|| execution.measuredResult().scale() > 3
							|| execution.measuredResult().precision() - execution.measuredResult().scale() > 9)) {
				throw invalid("Measured result must be non-negative and fit NUMERIC(12,3)");
			}
			if (execution.usesSpikes() == null) {
				throw invalid("Spike usage must be confirmed for every execution");
			}
			if (execution.id() != null) {
				TrainingImportDraftExecution old = existing.executionsById().get(execution.id());
				if (old == null || !usedExecutionIds.add(execution.id())
						|| row.id() == null
						|| !old.getAthleteRow().getId().equals(row.id())
						|| targetSegment.id() == null
						|| !old.getSegment().getId().equals(targetSegment.id())) {
					throw invalid("An execution ID must refer to the same athlete row and segment");
				}
				if (TrainingImportRecognitionMetadata.isUncertain(old.getRecognitionMetadata())
						&& !execution.recognitionReviewed()) {
					throw invalid("Uncertain execution information must be confirmed or corrected");
				}
			}
		}
	}

	private Map<String, Object> metadataForSegment(
			TrainingImportDraftUpdateRequest.Segment segment,
			DraftData existing) {
		return segment.id() == null ? Map.of()
				: existing.segmentsById().get(segment.id()).getRecognitionMetadata();
	}

	private Map<String, Object> existingRootMetadata(DraftData existing) {
		return existing.draft().getRecognitionMetadata();
	}

	private InvalidTrainingImportDraftException invalid(String message) {
		return new InvalidTrainingImportDraftException(message);
	}

	private record SequenceKey(long definitionId, int sequenceNumber) {
	}

	private record DraftData(
			TrainingImportDraft draft,
			List<TrainingImportDraftDefinition> definitions,
			List<TrainingImportDraftSegment> segments,
			List<TrainingImportDraftAthleteRow> athleteRows,
			List<TrainingImportDraftExecution> executions) {

		Map<Long, TrainingImportDraftDefinition> definitionsById() {
			Map<Long, TrainingImportDraftDefinition> result = new HashMap<>();
			for (TrainingImportDraftDefinition definition : definitions) {
				result.put(definition.getId(), definition);
			}
			return result;
		}

		Map<Long, TrainingImportDraftSegment> segmentsById() {
			Map<Long, TrainingImportDraftSegment> result = new HashMap<>();
			for (TrainingImportDraftSegment segment : segments) {
				result.put(segment.getId(), segment);
			}
			return result;
		}

		Map<Long, TrainingImportDraftAthleteRow> athleteRowsById() {
			Map<Long, TrainingImportDraftAthleteRow> result = new HashMap<>();
			for (TrainingImportDraftAthleteRow row : athleteRows) {
				result.put(row.getId(), row);
			}
			return result;
		}

		Map<Long, TrainingImportDraftExecution> executionsById() {
			Map<Long, TrainingImportDraftExecution> result = new HashMap<>();
			for (TrainingImportDraftExecution execution : executions) {
				result.put(execution.getId(), execution);
			}
			return result;
		}
	}
}

package pl.tfhub.api.trainingimport.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.tfhub.api.training.dto.request.AddTrainingParticipationRequest;
import pl.tfhub.api.training.dto.request.CompleteTrainingRequest;
import pl.tfhub.api.training.dto.request.CreateTrainingDefinitionRequest;
import pl.tfhub.api.training.dto.request.CreateTrainingRequest;
import pl.tfhub.api.training.dto.request.CreateTrainingSegmentRequest;
import pl.tfhub.api.training.dto.request.NewTrainingExecutionRequest;
import pl.tfhub.api.training.dto.response.CreateTrainingResponse;
import pl.tfhub.api.training.dto.response.TrainingDetailsResponse;
import pl.tfhub.api.training.service.TrainingCompletionService;
import pl.tfhub.api.training.service.TrainingCreationService;
import pl.tfhub.api.training.service.TrainingDetailsService;
import pl.tfhub.api.trainingimport.dto.response.TrainingImportAcceptanceResponse;
import pl.tfhub.api.trainingimport.dto.response.TrainingImportDraftResponse;
import pl.tfhub.api.trainingimport.dto.response.TrainingImportRejectionResponse;
import pl.tfhub.api.trainingimport.entity.TrainingImport;
import pl.tfhub.api.trainingimport.enums.TrainingImportAthleteMatchStatus;
import pl.tfhub.api.trainingimport.enums.TrainingImportStatus;
import pl.tfhub.api.trainingimport.exception.InvalidTrainingImportDraftException;
import pl.tfhub.api.trainingimport.exception.TrainingImportNotReviewableException;
import pl.tfhub.api.trainingimport.repository.TrainingImportRepository;

@Service
public class TrainingImportFinalizationService {

	private final TrainingImportAccessService accessService;
	private final TrainingImportRepository importRepository;
	private final TrainingImportDraftService draftService;
	private final TrainingCreationService trainingCreationService;
	private final TrainingDetailsService trainingDetailsService;
	private final TrainingCompletionService trainingCompletionService;

	public TrainingImportFinalizationService(
			TrainingImportAccessService accessService,
			TrainingImportRepository importRepository,
			TrainingImportDraftService draftService,
			TrainingCreationService trainingCreationService,
			TrainingDetailsService trainingDetailsService,
			TrainingCompletionService trainingCompletionService) {
		this.accessService = accessService;
		this.importRepository = importRepository;
		this.draftService = draftService;
		this.trainingCreationService = trainingCreationService;
		this.trainingDetailsService = trainingDetailsService;
		this.trainingCompletionService = trainingCompletionService;
	}

	@Transactional
	public TrainingImportAcceptanceResponse accept(long importId) {
		TrainingImport trainingImport = getReviewableImportForUpdate(importId);
		TrainingImportDraftResponse draft = draftService.getDraft(importId);
		if (!draft.validation().readyForAcceptance()) {
			var distanceIssue = draft.validation().issues().stream()
					.filter(issue -> issue.code().equals("SEGMENT_DISTANCE_UNRESOLVED"))
					.findFirst();
			if (distanceIssue.isPresent()) {
				throw new InvalidTrainingImportDraftException(distanceIssue.get().message());
			}
			throw new InvalidTrainingImportDraftException(
					"Training import draft contains unresolved validation issues");
		}

		CreateTrainingRequest createRequest = toCreateRequest(trainingImport, draft);
		CreateTrainingResponse created = trainingCreationService.createTraining(createRequest);
		TrainingDetailsResponse training = trainingDetailsService.getTraining(created.id());
		Map<Integer, Long> definitionIds = new HashMap<>();
		Map<SequenceKey, Long> segmentIds = new HashMap<>();
		for (var definition : training.definitions()) {
			definitionIds.put(definition.sequenceNumber(), definition.id());
			for (var segment : definition.segments()) {
				segmentIds.put(
						new SequenceKey(definition.sequenceNumber(), segment.sequenceNumber()),
						segment.id());
			}
		}

		List<AddTrainingParticipationRequest> participations = new ArrayList<>();
		for (TrainingImportDraftResponse.Definition definition : draft.definitions()) {
			for (TrainingImportDraftResponse.AthleteRow row : definition.athleteRows()) {
				if (row.matchStatus() == TrainingImportAthleteMatchStatus.SKIPPED) {
					continue;
				}
				if (row.matchStatus() != TrainingImportAthleteMatchStatus.MATCHED || row.athlete() == null) {
					throw new InvalidTrainingImportDraftException(
							"Every athlete row must be matched or skipped before acceptance");
				}
				Long definitionId = definitionIds.get(definition.sequenceNumber());
				if (definitionId == null) {
					throw new InvalidTrainingImportDraftException(
							"A draft definition could not be mapped to the created training");
				}
				List<NewTrainingExecutionRequest> executions = row.executions().stream()
						.map(execution -> {
							Long segmentId = segmentIds.get(new SequenceKey(
									definition.sequenceNumber(), execution.segmentSequenceNumber()));
							if (segmentId == null || execution.usesSpikes() == null) {
								throw new InvalidTrainingImportDraftException(
										"Draft execution data could not be mapped to the created training");
							}
							return new NewTrainingExecutionRequest(
									segmentId,
									execution.status(),
									execution.measuredResult(),
									execution.usesSpikes());
						})
						.toList();
				participations.add(new AddTrainingParticipationRequest(
						definitionId, row.athlete().id(), executions));
			}
		}

		trainingCompletionService.completeTraining(
				created.id(), new CompleteTrainingRequest(participations, List.of(), List.of()));
		trainingImport.accept(created.id());
		importRepository.saveAndFlush(trainingImport);
		return new TrainingImportAcceptanceResponse(
				importId, TrainingImportStatus.ACCEPTED, created.id());
	}

	@Transactional
	public TrainingImportRejectionResponse reject(long importId) {
		TrainingImport trainingImport = getReviewableImportForUpdate(importId);
		trainingImport.reject();
		importRepository.saveAndFlush(trainingImport);
		return new TrainingImportRejectionResponse(importId, TrainingImportStatus.REJECTED);
	}

	private TrainingImport getReviewableImportForUpdate(long importId) {
		TrainingImport trainingImport = accessService.getAccessibleImportForUpdate(importId);
		if (trainingImport.getStatus() != TrainingImportStatus.REVIEW_REQUIRED) {
			throw new TrainingImportNotReviewableException();
		}
		return trainingImport;
	}

	private CreateTrainingRequest toCreateRequest(
			TrainingImport trainingImport,
			TrainingImportDraftResponse draft) {
		List<CreateTrainingDefinitionRequest> definitions = draft.definitions().stream()
				.map(definition -> new CreateTrainingDefinitionRequest(
						definition.name(),
						definition.sequenceNumber(),
						definition.segments().stream()
								.map(segment -> new CreateTrainingSegmentRequest(
										segment.sequenceNumber(),
										segment.distanceM(),
										segment.restAfterSeconds()))
								.toList()))
				.toList();
		return new CreateTrainingRequest(
				draft.name(),
				draft.trainingDate(),
				trainingImport.getTrainingGroup().getId(),
				definitions);
	}

	private record SequenceKey(int definitionSequence, int segmentSequence) {
	}
}

package pl.tfhub.api.training.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.tfhub.api.common.authorization.AccessScopeService;
import pl.tfhub.api.training.dto.response.TrainingDefinitionDetailsResponse;
import pl.tfhub.api.training.dto.response.TrainingDetailsResponse;
import pl.tfhub.api.training.dto.response.TrainingAssignedAthleteResponse;
import pl.tfhub.api.training.dto.response.TrainingExecutionDetailsResponse;
import pl.tfhub.api.training.dto.response.TrainingParticipationDetailsResponse;
import pl.tfhub.api.training.dto.response.TrainingSegmentDetailsResponse;
import pl.tfhub.api.training.dto.response.TrainingSectionDetailsResponse;
import pl.tfhub.api.training.entity.AthleteTrainingParticipation;
import pl.tfhub.api.training.entity.Training;
import pl.tfhub.api.training.entity.TrainingDefinition;
import pl.tfhub.api.training.entity.TrainingSegment;
import pl.tfhub.api.training.entity.TrainingSegmentExecution;
import pl.tfhub.api.training.exception.InvalidTrainingIdException;
import pl.tfhub.api.training.exception.TrainingNotFoundException;
import pl.tfhub.api.training.enums.TrainingType;
import pl.tfhub.api.training.repository.TrainingAssignmentRepository;
import pl.tfhub.api.training.repository.AthleteTrainingParticipationRepository;
import pl.tfhub.api.training.repository.TrainingDefinitionRepository;
import pl.tfhub.api.training.repository.TrainingRepository;
import pl.tfhub.api.training.repository.TrainingSegmentExecutionRepository;
import pl.tfhub.api.training.repository.TrainingSegmentRepository;
import pl.tfhub.api.training.repository.TrainingSectionRepository;
import pl.tfhub.api.traininggroup.dto.response.TrainingGroupResponse;
import pl.tfhub.api.user.enums.UserRole;

@Service
public class TrainingDetailsService {

    private final TrainingRepository trainingRepository;
    private final TrainingDefinitionRepository definitionRepository;
    private final TrainingSegmentRepository segmentRepository;
    private final AthleteTrainingParticipationRepository participationRepository;
    private final TrainingSegmentExecutionRepository executionRepository;
    private final TrainingSectionRepository sectionRepository;
    private final TrainingAssignmentRepository assignmentRepository;
    private final AccessScopeService accessScopeService;

    public TrainingDetailsService(
            TrainingRepository trainingRepository,
            TrainingDefinitionRepository definitionRepository,
            TrainingSegmentRepository segmentRepository,
            AthleteTrainingParticipationRepository participationRepository,
            TrainingSegmentExecutionRepository executionRepository,
            TrainingSectionRepository sectionRepository,
            TrainingAssignmentRepository assignmentRepository,
            AccessScopeService accessScopeService) {
        this.trainingRepository = trainingRepository;
        this.definitionRepository = definitionRepository;
        this.segmentRepository = segmentRepository;
        this.participationRepository = participationRepository;
        this.executionRepository = executionRepository;
        this.sectionRepository = sectionRepository;
        this.assignmentRepository = assignmentRepository;
        this.accessScopeService = accessScopeService;
    }

    @Transactional(readOnly = true)
    public TrainingDetailsResponse getTraining(long trainingId) {
        if (trainingId <= 0) {
            throw new InvalidTrainingIdException();
        }

        var user = accessScopeService.currentUser();
        Training training = trainingRepository.findById(trainingId)
                .orElseThrow(() -> new TrainingNotFoundException(trainingId));
        if (user.role() == UserRole.ATHLETE) {
            if (training.getTrainingType() != TrainingType.FREE_TEXT) {
                throw new AccessDeniedException("Athlete accounts cannot access TEMPO training details");
            }
            if (!assignmentRepository.isVisibleToAthlete(
                    trainingId, accessScopeService.currentAthleteId())) {
                throw new TrainingNotFoundException(trainingId);
            }
        } else if (user.role() == UserRole.COACH
                && !assignmentRepository.canCoachManage(trainingId, user.id())) {
            throw new TrainingNotFoundException(trainingId);
        } else if (user.role() != UserRole.ADMIN && user.role() != UserRole.COACH) {
            throw new AccessDeniedException("Training access is required");
        }

        List<TrainingDefinition> definitions = training.getTrainingType() == TrainingType.TEMPO
                ? definitionRepository.findAllByTraining_IdOrderBySequenceNumberAsc(trainingId)
                : List.of();
        List<Long> definitionIds = definitions.stream().map(TrainingDefinition::getId).toList();
        List<TrainingSegment> segments = definitionIds.isEmpty()
                ? List.of()
                : segmentRepository
                        .findAllByTrainingDefinition_IdInOrderByTrainingDefinition_IdAscSequenceNumberAsc(
                                definitionIds);
        List<AthleteTrainingParticipation> participations = training.getTrainingType() == TrainingType.TEMPO
                ? participationRepository.findAllDetailsByTrainingId(trainingId)
                : List.of();
        List<Long> participationIds = participations.stream()
                .map(AthleteTrainingParticipation::getId)
                .toList();
        List<TrainingSegmentExecution> executions = participationIds.isEmpty()
                ? List.of()
                : executionRepository.findAllByParticipation_IdIn(participationIds);
        List<TrainingSectionDetailsResponse> sections = sectionRepository
                .findAllByTraining_IdOrderBySequenceNumberAsc(trainingId).stream()
                .map(section -> new TrainingSectionDetailsResponse(
                        section.getId(),
                        section.getTitle(),
                        section.getContent(),
                        section.getSequenceNumber()))
                .toList();

        return toResponse(training, trainingId, user.role(), definitions, segments, participations, executions, sections);
    }

    private TrainingDetailsResponse toResponse(
            Training training,
            long trainingId,
            UserRole role,
            List<TrainingDefinition> definitions,
            List<TrainingSegment> segments,
            List<AthleteTrainingParticipation> participations,
            List<TrainingSegmentExecution> executions,
            List<TrainingSectionDetailsResponse> sections) {
        Map<Long, List<TrainingSegmentDetailsResponse>> segmentsByDefinition = new HashMap<>();
        Map<Long, TrainingSegmentDetailsResponse> segmentDetailsById = new HashMap<>();
        for (TrainingSegment segment : segments) {
            TrainingSegmentDetailsResponse response = new TrainingSegmentDetailsResponse(
                    segment.getId(),
                    segment.getSequenceNumber(),
                    segment.getDistanceM(),
                    segment.getRestAfterSeconds());
            segmentsByDefinition.computeIfAbsent(
                    segment.getTrainingDefinition().getId(), ignored -> new ArrayList<>()).add(response);
            segmentDetailsById.put(segment.getId(), response);
        }

        Map<Long, List<TrainingParticipationDetailsResponse>> participationsByDefinition = new HashMap<>();
        Map<Long, List<TrainingExecutionDetailsResponse>> executionsByParticipation = new HashMap<>();
        for (TrainingSegmentExecution execution : executions) {
            TrainingSegmentDetailsResponse segment = segmentDetailsById.get(execution.getTrainingSegment().getId());
            if (segment == null) {
                continue;
            }
            executionsByParticipation.computeIfAbsent(
                    execution.getParticipation().getId(), ignored -> new ArrayList<>())
                    .add(new TrainingExecutionDetailsResponse(
                            execution.getId(),
                            segment.id(),
                            segment.sequenceNumber(),
                            execution.getStatus(),
                            execution.getMeasuredResult(),
                            execution.isUsesSpikes()));
        }

        for (AthleteTrainingParticipation participation : participations) {
            var athlete = participation.getAthlete();
            List<TrainingExecutionDetailsResponse> participantExecutions =
                    executionsByParticipation.getOrDefault(participation.getId(), List.of()).stream()
                            .sorted(java.util.Comparator
                                    .comparingInt(TrainingExecutionDetailsResponse::segmentSequenceNumber)
                                    .thenComparingLong(TrainingExecutionDetailsResponse::segmentId))
                            .toList();
            participationsByDefinition.computeIfAbsent(
                    participation.getTrainingDefinition().getId(), ignored -> new ArrayList<>())
                    .add(new TrainingParticipationDetailsResponse(
                            participation.getId(),
                            athlete.getId(),
                            athlete.getFirstName(),
                            athlete.getLastName(),
                            participantExecutions));
        }

        List<TrainingDefinitionDetailsResponse> definitionResponses = definitions.stream()
                .map(definition -> new TrainingDefinitionDetailsResponse(
                        definition.getId(),
                        definition.getName(),
                        definition.getSequenceNumber(),
                        List.copyOf(segmentsByDefinition.getOrDefault(definition.getId(), List.of())),
                        List.copyOf(participationsByDefinition.getOrDefault(definition.getId(), List.of()))))
                .toList();

        List<TrainingAssignedAthleteResponse> assignedAthletes =
                role == UserRole.ATHLETE
                        ? List.of()
                        : assignmentRepository.findAssignedAthletes(trainingId);
        var trainingGroup = training.getTrainingGroup() == null
                ? null
                : new TrainingGroupResponse(
                        training.getTrainingGroup().getId(),
                        training.getTrainingGroup().getName());

        return new TrainingDetailsResponse(
                training.getId(),
                training.getName(),
                training.getTrainingDate(),
                training.getStatus(),
                trainingGroup,
                training.getAssignmentType(),
                assignedAthletes,
                definitionResponses,
                training.getTrainingType(),
                sections,
                role != UserRole.ATHLETE && training.getTrainingType() == TrainingType.FREE_TEXT
                        ? training.getEquipment() : null);
    }
}

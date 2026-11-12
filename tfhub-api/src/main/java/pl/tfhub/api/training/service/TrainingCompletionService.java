package pl.tfhub.api.training.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.tfhub.api.athlete.exception.AthleteNotFoundException;
import pl.tfhub.api.common.authorization.AccessScopeService;
import pl.tfhub.api.training.dto.request.AddTrainingParticipationRequest;
import pl.tfhub.api.training.dto.request.CompleteTrainingRequest;
import pl.tfhub.api.training.dto.request.EditTrainingExecutionRequest;
import pl.tfhub.api.training.dto.request.NewTrainingExecutionRequest;
import pl.tfhub.api.training.dto.request.UpdateTrainingParticipationRequest;
import pl.tfhub.api.training.dto.response.UpdateTrainingResponse;
import pl.tfhub.api.training.entity.AthleteTrainingParticipation;
import pl.tfhub.api.training.entity.Training;
import pl.tfhub.api.training.entity.TrainingDefinition;
import pl.tfhub.api.training.entity.TrainingSegment;
import pl.tfhub.api.training.entity.TrainingSegmentExecution;
import pl.tfhub.api.training.enums.TrainingExecutionStatus;
import pl.tfhub.api.training.exception.InvalidTrainingIdException;
import pl.tfhub.api.training.exception.InvalidTrainingRequestException;
import pl.tfhub.api.training.exception.TrainingNotFoundException;
import pl.tfhub.api.training.repository.AthleteTrainingParticipationRepository;
import pl.tfhub.api.training.repository.TrainingCreationRepository;
import pl.tfhub.api.training.repository.TrainingDefinitionRepository;
import pl.tfhub.api.training.repository.TrainingRepository;
import pl.tfhub.api.training.repository.TrainingSegmentExecutionRepository;
import pl.tfhub.api.athlete.repository.AthleteRepository;
import pl.tfhub.api.training.repository.TrainingSegmentRepository;

@Service
public class TrainingCompletionService {

    private final TrainingRepository trainingRepository;
    private final TrainingDefinitionRepository definitionRepository;
    private final TrainingSegmentRepository segmentRepository;
    private final AthleteTrainingParticipationRepository participationRepository;
    private final TrainingSegmentExecutionRepository executionRepository;
    private final TrainingCreationRepository trainingCreationRepository;
    private final AthleteRepository athleteRepository;
    private final AccessScopeService accessScopeService;

    public TrainingCompletionService(
            TrainingRepository trainingRepository,
            TrainingDefinitionRepository definitionRepository,
            TrainingSegmentRepository segmentRepository,
            AthleteTrainingParticipationRepository participationRepository,
            TrainingSegmentExecutionRepository executionRepository,
            TrainingCreationRepository trainingCreationRepository,
            AthleteRepository athleteRepository,
            AccessScopeService accessScopeService) {
        this.trainingRepository = trainingRepository;
        this.definitionRepository = definitionRepository;
        this.segmentRepository = segmentRepository;
        this.participationRepository = participationRepository;
        this.executionRepository = executionRepository;
        this.trainingCreationRepository = trainingCreationRepository;
        this.athleteRepository = athleteRepository;
        this.accessScopeService = accessScopeService;
    }

    @Transactional
    public UpdateTrainingResponse completeTraining(long trainingId, CompleteTrainingRequest request) {
        applyTrainingChanges(trainingId, request);
        return new UpdateTrainingResponse(trainingId);
    }

    private void applyTrainingChanges(long trainingId, CompleteTrainingRequest request) {
        accessScopeService.requireCoachOrAdmin();
        if (trainingId <= 0) {
            throw new InvalidTrainingIdException();
        }
        Training training = trainingRepository.findByIdForUpdate(trainingId)
                .orElseThrow(() -> new TrainingNotFoundException(trainingId));
        accessScopeService.requireGroupAssignment(training.getTrainingGroup().getId());
        if (request == null || request.add() == null || request.update() == null
                || request.removeParticipationIds() == null) {
            throw new InvalidTrainingRequestException("Add, update, and removal collections are required");
        }

        List<TrainingDefinition> definitions =
                definitionRepository.findAllByTraining_IdOrderBySequenceNumberAsc(trainingId);
        Map<Long, TrainingDefinition> definitionsById = indexById(definitions, TrainingDefinition::getId);
        List<Long> definitionIds = definitions.stream().map(TrainingDefinition::getId).toList();
        List<TrainingSegment> segments = definitionIds.isEmpty()
                ? List.of()
                : segmentRepository
                        .findAllByTrainingDefinition_IdInOrderByTrainingDefinition_IdAscSequenceNumberAsc(
                                definitionIds);
        Map<Long, TrainingSegment> segmentsById = indexById(segments, TrainingSegment::getId);
        List<AthleteTrainingParticipation> participations =
                participationRepository.findAllByTraining_IdOrderByTrainingDefinition_IdAscIdAsc(trainingId);
        Map<Long, AthleteTrainingParticipation> participationsById =
                indexById(participations, AthleteTrainingParticipation::getId);
        List<Long> participationIds = participations.stream()
                .map(AthleteTrainingParticipation::getId)
                .toList();
        List<TrainingSegmentExecution> executions = participationIds.isEmpty()
                ? List.of()
                : executionRepository.findAllByParticipation_IdIn(participationIds);
        Map<Long, TrainingSegmentExecution> executionsById =
                indexById(executions, TrainingSegmentExecution::getId);

        CompletionPlan plan = validateAndPlan(
                request, definitionsById, segments, segmentsById, participationsById, executionsById, executions,
                training.getTrainingGroup().getId());
        applyPlan(training, plan, participationsById);
        training.markCompleted();
    }

    private CompletionPlan validateAndPlan(
            CompleteTrainingRequest request,
            Map<Long, TrainingDefinition> definitionsById,
            List<TrainingSegment> segments,
            Map<Long, TrainingSegment> segmentsById,
            Map<Long, AthleteTrainingParticipation> participationsById,
            Map<Long, TrainingSegmentExecution> executionsById,
            List<TrainingSegmentExecution> executions,
            long trainingGroupId) {
        Set<Long> removedIds = new HashSet<>();
        for (Long id : request.removeParticipationIds()) {
            if (id == null || !removedIds.add(id) || !participationsById.containsKey(id)) {
                throw new InvalidTrainingRequestException(
                        "A removal ID is invalid, duplicated, or does not belong to this training");
            }
        }

        Map<Long, List<TrainingSegmentExecution>> executionsByParticipation = new HashMap<>();
        for (TrainingSegmentExecution execution : executions) {
            executionsByParticipation.computeIfAbsent(
                    execution.getParticipation().getId(), ignored -> new ArrayList<>()).add(execution);
        }
        for (Long id : removedIds) {
            if (executionsByParticipation.getOrDefault(id, List.of()).stream()
                    .anyMatch(execution -> execution.getStatus() == TrainingExecutionStatus.RECORDED)) {
                throw new InvalidTrainingRequestException(
                        "A participation with recorded execution data cannot be removed or reassigned");
            }
        }

        Set<Long> updatedParticipationIds = new HashSet<>();
        Set<Long> updatedExecutionIds = new HashSet<>();
        for (UpdateTrainingParticipationRequest update : request.update()) {
            if (update == null || update.id() == null || removedIds.contains(update.id())
                || update.executionUpdates() == null || !updatedParticipationIds.add(update.id())) {
                throw new InvalidTrainingRequestException("A participation update is invalid or duplicated");
            }
            AthleteTrainingParticipation participation = participationsById.get(update.id());
            if (participation == null) {
                throw new InvalidTrainingRequestException(
                        "A participation ID does not belong to this training");
            }
            Set<Long> validSegmentIds = segmentsById.values().stream()
                    .filter(segment -> segment.getTrainingDefinition().getId()
                            .equals(participation.getTrainingDefinition().getId()))
                    .map(TrainingSegment::getId)
                    .collect(java.util.stream.Collectors.toSet());
            for (EditTrainingExecutionRequest executionUpdate : update.executionUpdates()) {
                TrainingSegmentExecution execution = executionUpdate == null
                        ? null
                        : executionsById.get(executionUpdate.id());
                if (execution == null
                        || !execution.getParticipation().getId().equals(update.id())
                        || !validSegmentIds.contains(execution.getTrainingSegment().getId())
                        || !updatedExecutionIds.add(executionUpdate.id())) {
                    throw new InvalidTrainingRequestException(
                            "An execution ID is invalid, duplicated, or outside this participation");
                }
                validateExecution(executionUpdate.status(), executionUpdate.measuredResult(),
                        executionUpdate.usesSpikes());
            }
        }

        Set<Long> assignedAthleteIds = new HashSet<>();
        for (AthleteTrainingParticipation participation : participationsById.values()) {
            if (!removedIds.contains(participation.getId())
                    && !assignedAthleteIds.add(participation.getAthlete().getId())) {
                throw new InvalidTrainingRequestException(
                        "An athlete is already assigned more than once in this training");
            }
        }
        List<Long> newAthleteIds = new ArrayList<>();
        List<AdditionPlan> additions = new ArrayList<>();
        Set<String> additionKeys = new HashSet<>();
        for (AddTrainingParticipationRequest add : request.add()) {
            if (add == null || add.executionUpdates() == null) {
                throw new InvalidTrainingRequestException("A participation addition is required");
            }
            TrainingDefinition definition = definitionsById.get(add.definitionId());
            if (definition == null || add.athleteId() == null
                    || !assignedAthleteIds.add(add.athleteId())) {
                throw new InvalidTrainingRequestException(
                        "A definition or athlete is invalid, or the athlete is already assigned");
            }
            String key = definition.getId() + ":" + add.athleteId();
            if (!additionKeys.add(key)) {
                throw new InvalidTrainingRequestException("A participation addition is duplicated");
            }
            Set<Long> seenSegments = new HashSet<>();
            for (NewTrainingExecutionRequest execution : add.executionUpdates()) {
                TrainingSegment segment = execution == null ? null : segmentsById.get(execution.segmentId());
                if (segment == null || !segment.getTrainingDefinition().getId().equals(definition.getId())
                        || !seenSegments.add(execution.segmentId())) {
                    throw new InvalidTrainingRequestException(
                            "An initial execution segment is invalid or duplicated");
                }
                validateExecution(execution.status(), execution.measuredResult(), execution.usesSpikes());
            }
            newAthleteIds.add(add.athleteId());
            List<TrainingSegment> definitionSegments = segments.stream()
                    .filter(segment -> segment.getTrainingDefinition().getId().equals(definition.getId()))
                    .toList();
            additions.add(new AdditionPlan(add, definition, definitionSegments));
        }
        validateNewAthletes(newAthleteIds, trainingGroupId);
        return new CompletionPlan(
                List.copyOf(request.removeParticipationIds()),
                List.copyOf(request.update()),
                additions,
                executionsByParticipation,
                executionsById);
    }

    private void validateNewAthletes(List<Long> athleteIds, long trainingGroupId) {
        if (athleteIds.isEmpty()) return;
        Map<Long, Long> athleteGroups = new HashMap<>();
        for (TrainingCreationRepository.AthleteTrainingGroup row
                : trainingCreationRepository.findAthleteTrainingGroups(athleteIds)) {
            athleteGroups.put(row.athleteId(), row.trainingGroupId());
        }
        for (Long athleteId : athleteIds) {
            if (!athleteGroups.containsKey(athleteId)) {
                throw new AthleteNotFoundException(athleteId);
            }
            if (!Long.valueOf(trainingGroupId).equals(athleteGroups.get(athleteId))) {
                throw new InvalidTrainingRequestException(
                        "Every newly assigned athlete must currently belong to the training group");
            }
        }
    }

    private void validateExecution(
            TrainingExecutionStatus status, java.math.BigDecimal measuredResult, Boolean usesSpikes) {
        if (status == null || usesSpikes == null) {
            throw new InvalidTrainingRequestException("Execution status and spike usage are required");
        }
        if ((status == TrainingExecutionStatus.RECORDED) != (measuredResult != null)) {
            throw new InvalidTrainingRequestException(
                    "A measured result is required only for RECORDED executions");
        }
        if (measuredResult != null && measuredResult.signum() < 0) {
            throw new InvalidTrainingRequestException("Measured result must not be negative");
        }
    }

    private void applyPlan(
            Training training,
            CompletionPlan plan,
            Map<Long, AthleteTrainingParticipation> participationsById) {
        List<TrainingSegmentExecution> executionsToDelete = plan.removeParticipationIds().stream()
                .flatMap(id -> plan.executionsByParticipation().getOrDefault(id, List.of()).stream())
                .toList();
        executionRepository.deleteAll(executionsToDelete);
        executionRepository.flush();
        participationRepository.deleteAll(plan.removeParticipationIds().stream()
                .map(participationsById::get)
                .toList());
        participationRepository.flush();

        for (UpdateTrainingParticipationRequest update : plan.updates()) {
            for (EditTrainingExecutionRequest executionUpdate : update.executionUpdates()) {
                TrainingSegmentExecution execution = plan.executionsById().get(executionUpdate.id());
                execution.update(executionUpdate.status(), executionUpdate.measuredResult(),
                        executionUpdate.usesSpikes());
            }
        }

        List<AthleteTrainingParticipation> newParticipations = new ArrayList<>();
        for (AdditionPlan addition : plan.additions()) {
            newParticipations.add(new AthleteTrainingParticipation(
                    training,
                    addition.definition(),
                    athleteRepository.getReferenceById(addition.request().athleteId())));
        }
        participationRepository.saveAllAndFlush(newParticipations);

        List<TrainingSegmentExecution> newExecutions = new ArrayList<>();
        for (int index = 0; index < newParticipations.size(); index++) {
            AthleteTrainingParticipation participation = newParticipations.get(index);
            AdditionPlan addition = plan.additions().get(index);
            Map<Long, NewTrainingExecutionRequest> updatesBySegment = new HashMap<>();
            addition.request().executionUpdates().forEach(value ->
                    updatesBySegment.put(value.segmentId(), value));
            for (TrainingSegment segment : addition.definitionSegments()) {
                NewTrainingExecutionRequest update = updatesBySegment.get(segment.getId());
                TrainingSegmentExecution execution = new TrainingSegmentExecution(
                        participation,
                        segment,
                        participation.getTrainingDefinition(),
                        update == null ? TrainingExecutionStatus.PENDING : update.status(),
                        update == null ? null : update.measuredResult(),
                        update != null && update.usesSpikes());
                newExecutions.add(execution);
            }
        }
        executionRepository.saveAllAndFlush(newExecutions);
    }

    private <T> Map<Long, T> indexById(List<T> entities, IdFunction<T> idFunction) {
        Map<Long, T> result = new HashMap<>();
        for (T entity : entities) result.put(idFunction.id(entity), entity);
        return result;
    }

    private interface IdFunction<T> {
        Long id(T value);
    }

    private record AdditionPlan(
            AddTrainingParticipationRequest request,
            TrainingDefinition definition,
            List<TrainingSegment> definitionSegments) {
    }

    private record CompletionPlan(
            List<Long> removeParticipationIds,
            List<UpdateTrainingParticipationRequest> updates,
            List<AdditionPlan> additions,
            Map<Long, List<TrainingSegmentExecution>> executionsByParticipation,
            Map<Long, TrainingSegmentExecution> executionsById) {
    }
}

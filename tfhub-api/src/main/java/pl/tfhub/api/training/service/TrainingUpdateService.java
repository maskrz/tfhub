package pl.tfhub.api.training.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.tfhub.api.common.authorization.AccessScopeService;
import pl.tfhub.api.training.dto.request.EditTrainingDefinitionRequest;
import pl.tfhub.api.training.dto.request.EditTrainingRequest;
import pl.tfhub.api.training.dto.request.EditTrainingSegmentRequest;
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
import pl.tfhub.api.training.repository.TrainingDefinitionRepository;
import pl.tfhub.api.training.repository.TrainingRepository;
import pl.tfhub.api.training.repository.TrainingSegmentExecutionRepository;
import pl.tfhub.api.training.repository.TrainingSegmentRepository;

@Service
public class TrainingUpdateService {

    private final TrainingRepository trainingRepository;
    private final TrainingDefinitionRepository definitionRepository;
    private final TrainingSegmentRepository segmentRepository;
    private final AthleteTrainingParticipationRepository participationRepository;
    private final TrainingSegmentExecutionRepository executionRepository;
    private final AccessScopeService accessScopeService;

    public TrainingUpdateService(
            TrainingRepository trainingRepository,
            TrainingDefinitionRepository definitionRepository,
            TrainingSegmentRepository segmentRepository,
            AthleteTrainingParticipationRepository participationRepository,
            TrainingSegmentExecutionRepository executionRepository,
            AccessScopeService accessScopeService) {
        this.trainingRepository = trainingRepository;
        this.definitionRepository = definitionRepository;
        this.segmentRepository = segmentRepository;
        this.participationRepository = participationRepository;
        this.executionRepository = executionRepository;
        this.accessScopeService = accessScopeService;
    }

    @Transactional
    public UpdateTrainingResponse updateTraining(long trainingId, EditTrainingRequest request) {
        accessScopeService.requireCoachOrAdmin();
        if (trainingId <= 0) {
            throw new InvalidTrainingIdException();
        }

        Training training = trainingRepository.findByIdForUpdate(trainingId)
                .orElseThrow(() -> new TrainingNotFoundException(trainingId));
        accessScopeService.requireGroupAssignment(training.getTrainingGroup().getId());
        List<TrainingDefinition> currentDefinitions =
                definitionRepository.findAllByTraining_IdOrderBySequenceNumberAsc(trainingId);
        List<Long> definitionIds = currentDefinitions.stream().map(TrainingDefinition::getId).toList();
        List<TrainingSegment> currentSegments = definitionIds.isEmpty()
                ? List.of()
                : segmentRepository
                        .findAllByTrainingDefinition_IdInOrderByTrainingDefinition_IdAscSequenceNumberAsc(
                                definitionIds);
        List<AthleteTrainingParticipation> currentParticipations =
                participationRepository.findAllByTraining_IdOrderByTrainingDefinition_IdAscIdAsc(trainingId);
        List<Long> participationIds = currentParticipations.stream()
                .map(AthleteTrainingParticipation::getId)
                .toList();
        List<TrainingSegmentExecution> currentExecutions = participationIds.isEmpty()
                ? List.of()
                : executionRepository.findAllByParticipation_IdIn(participationIds);

        EditPlan plan = validateAndPlan(request, training, currentDefinitions, currentSegments, currentExecutions);
        applyPlan(training, request, plan, currentDefinitions, currentSegments, currentParticipations, currentExecutions);
        return new UpdateTrainingResponse(trainingId);
    }

    private EditPlan validateAndPlan(
            EditTrainingRequest request,
            Training training,
            List<TrainingDefinition> currentDefinitions,
            List<TrainingSegment> currentSegments,
            List<TrainingSegmentExecution> currentExecutions) {
        Map<Long, TrainingDefinition> definitionsById = indexById(currentDefinitions, TrainingDefinition::getId);
        Map<Long, TrainingSegment> segmentsById = indexById(currentSegments, TrainingSegment::getId);
        Set<Integer> definitionSequences = new HashSet<>();
        Set<Long> retainedDefinitionIds = new HashSet<>();
        Set<Long> retainedSegmentIds = new HashSet<>();
        List<DefinitionPlan> definitionPlans = new ArrayList<>();

        for (EditTrainingDefinitionRequest definitionRequest : request.definitions()) {
            if (!definitionSequences.add(definitionRequest.sequenceNumber())) {
                throw new InvalidTrainingRequestException("Definition sequence numbers must be unique");
            }
            boolean newDefinition = definitionRequest.id() == null;
            TrainingDefinition definition;
            if (newDefinition) {
                definition = new TrainingDefinition(
                        training, definitionRequest.name(), definitionRequest.sequenceNumber());
            } else {
                definition = definitionsById.get(definitionRequest.id());
                if (definition == null || !retainedDefinitionIds.add(definitionRequest.id())) {
                    throw new InvalidTrainingRequestException(
                            "A definition ID is invalid, duplicated, or belongs to another training");
                }
            }

            Set<Integer> segmentSequences = new HashSet<>();
            List<SegmentPlan> segmentPlans = new ArrayList<>();
            for (EditTrainingSegmentRequest segmentRequest : definitionRequest.segments()) {
                if (!segmentSequences.add(segmentRequest.sequenceNumber())) {
                    throw new InvalidTrainingRequestException(
                            "Segment sequence numbers must be unique within each definition");
                }
                boolean newSegment = segmentRequest.id() == null;
                TrainingSegment segment;
                if (newSegment) {
                    segment = new TrainingSegment(
                            definition,
                            segmentRequest.sequenceNumber(),
                            segmentRequest.distanceM(),
                            segmentRequest.restAfterSeconds());
                } else {
                    segment = segmentsById.get(segmentRequest.id());
                    if (segment == null
                            || newDefinition
                            || !segment.getTrainingDefinition().getId().equals(definition.getId())
                            || !retainedSegmentIds.add(segmentRequest.id())) {
                        throw new InvalidTrainingRequestException(
                                "A segment ID is invalid, duplicated, or belongs to another definition");
                    }
                }
                segmentPlans.add(new SegmentPlan(segmentRequest, segment, newSegment));
            }
            definitionPlans.add(new DefinitionPlan(
                    definitionRequest, definition, newDefinition, segmentPlans));
        }

        Set<Long> allDefinitionIds = new HashSet<>(definitionsById.keySet());
        Set<Long> removedDefinitionIds = difference(allDefinitionIds, retainedDefinitionIds);
        Set<Long> allSegmentIds = new HashSet<>(segmentsById.keySet());
        Set<Long> removedSegmentIds = difference(allSegmentIds, retainedSegmentIds);
        Set<Long> recordedSegmentIds = new HashSet<>();
        for (TrainingSegmentExecution execution : currentExecutions) {
            if (execution.getStatus() != TrainingExecutionStatus.RECORDED) {
                continue;
            }
            long definitionId = execution.getTrainingDefinition().getId();
            long segmentId = execution.getTrainingSegment().getId();
            if (removedDefinitionIds.contains(definitionId)) {
                throw new InvalidTrainingRequestException(
                        "A definition with recorded execution data cannot be removed");
            }
            if (removedSegmentIds.contains(segmentId)) {
                throw new InvalidTrainingRequestException(
                        "A segment with recorded execution data cannot be removed");
            }
            recordedSegmentIds.add(segmentId);
        }
        for (DefinitionPlan definitionPlan : definitionPlans) {
            for (SegmentPlan segmentPlan : definitionPlan.segments()) {
                if (!segmentPlan.isNew()
                        && recordedSegmentIds.contains(segmentPlan.entity().getId())
                        && segmentPlan.entity().getDistanceM() != segmentPlan.request().distanceM()) {
                    throw new InvalidTrainingRequestException(
                            "The distance of a segment with recorded execution data cannot be changed");
                }
            }
        }
        return new EditPlan(definitionPlans, removedDefinitionIds, removedSegmentIds);
    }

    private void applyPlan(
            Training training,
            EditTrainingRequest request,
            EditPlan plan,
            List<TrainingDefinition> currentDefinitions,
            List<TrainingSegment> currentSegments,
            List<AthleteTrainingParticipation> currentParticipations,
            List<TrainingSegmentExecution> currentExecutions) {
        deleteRemovedRows(plan, currentDefinitions, currentSegments, currentParticipations, currentExecutions);
        training.updateDetails(request.name(), request.trainingDate());

        List<DefinitionPlan> retainedDefinitions = plan.definitions().stream()
                .filter(definitionPlan -> !definitionPlan.isNew())
                .toList();
        if (retainedDefinitions.stream().anyMatch(value ->
                value.entity().getSequenceNumber() != value.request().sequenceNumber())) {
            Set<Integer> reserved = new HashSet<>();
            retainedDefinitions.forEach(value -> reserved.add(value.entity().getSequenceNumber()));
            retainedDefinitions.forEach(value -> reserved.add(value.request().sequenceNumber()));
            int[] temporarySequences = temporarySequences(reserved, retainedDefinitions.size());
            for (int index = 0; index < retainedDefinitions.size(); index++) {
                TrainingDefinition entity = retainedDefinitions.get(index).entity();
                entity.update(entity.getName(), temporarySequences[index]);
            }
            definitionRepository.flush();
        }

        List<TrainingDefinition> newDefinitions = new ArrayList<>();
        for (DefinitionPlan definitionPlan : plan.definitions()) {
            if (definitionPlan.isNew()) {
                newDefinitions.add(definitionPlan.entity());
            } else {
                definitionPlan.entity().update(
                        definitionPlan.request().name(), definitionPlan.request().sequenceNumber());
            }
        }
        definitionRepository.saveAllAndFlush(newDefinitions);

        stageSegmentSequences(plan.definitions());
        List<TrainingSegment> newSegments = new ArrayList<>();
        for (DefinitionPlan definitionPlan : plan.definitions()) {
            for (SegmentPlan segmentPlan : definitionPlan.segments()) {
                if (segmentPlan.isNew()) {
                    newSegments.add(segmentPlan.entity());
                } else {
                    segmentPlan.entity().update(
                            segmentPlan.request().sequenceNumber(),
                            segmentPlan.request().distanceM(),
                            segmentPlan.request().restAfterSeconds());
                }
            }
        }
        segmentRepository.saveAllAndFlush(newSegments);

        Set<ExecutionPair> existingPairs = new HashSet<>();
        for (TrainingSegmentExecution execution : currentExecutions) {
            existingPairs.add(new ExecutionPair(
                    execution.getParticipation().getId(), execution.getTrainingSegment().getId()));
        }
        Map<Long, List<AthleteTrainingParticipation>> participationsByDefinition = new HashMap<>();
        for (AthleteTrainingParticipation participation : currentParticipations) {
            if (!plan.removedDefinitionIds().contains(participation.getTrainingDefinition().getId())) {
                participationsByDefinition.computeIfAbsent(
                        participation.getTrainingDefinition().getId(), ignored -> new ArrayList<>()).add(participation);
            }
        }
        List<TrainingSegmentExecution> newExecutions = new ArrayList<>();
        for (DefinitionPlan definitionPlan : plan.definitions()) {
            for (SegmentPlan segmentPlan : definitionPlan.segments()) {
                if (!segmentPlan.isNew()) {
                    continue;
                }
                List<AthleteTrainingParticipation> participants = participationsByDefinition.getOrDefault(
                        definitionPlan.entity().getId(), List.of());
                for (AthleteTrainingParticipation participation : participants) {
                    ExecutionPair pair = new ExecutionPair(participation.getId(), segmentPlan.entity().getId());
                    if (existingPairs.add(pair)) {
                        newExecutions.add(new TrainingSegmentExecution(
                                participation,
                                segmentPlan.entity(),
                                definitionPlan.entity(),
                                TrainingExecutionStatus.PENDING,
                                null,
                                false));
                    }
                }
            }
        }
        executionRepository.saveAllAndFlush(newExecutions);
    }

    private void deleteRemovedRows(
            EditPlan plan,
            List<TrainingDefinition> definitions,
            List<TrainingSegment> segments,
            List<AthleteTrainingParticipation> participations,
            List<TrainingSegmentExecution> executions) {
        List<TrainingSegmentExecution> executionsToDelete = executions.stream()
                .filter(execution ->
                        plan.removedDefinitionIds().contains(execution.getTrainingDefinition().getId())
                                || plan.removedSegmentIds().contains(execution.getTrainingSegment().getId()))
                .toList();
        executionRepository.deleteAll(executionsToDelete);
        executionRepository.flush();

        List<AthleteTrainingParticipation> participationsToDelete = participations.stream()
                .filter(participation ->
                        plan.removedDefinitionIds().contains(participation.getTrainingDefinition().getId()))
                .toList();
        participationRepository.deleteAll(participationsToDelete);
        participationRepository.flush();

        segmentRepository.deleteAll(segments.stream()
                .filter(segment -> plan.removedSegmentIds().contains(segment.getId()))
                .toList());
        segmentRepository.flush();
        definitionRepository.deleteAll(definitions.stream()
                .filter(definition -> plan.removedDefinitionIds().contains(definition.getId()))
                .toList());
        definitionRepository.flush();
    }

    private void stageSegmentSequences(List<DefinitionPlan> definitionPlans) {
        for (DefinitionPlan definitionPlan : definitionPlans) {
            List<SegmentPlan> retained = definitionPlan.segments().stream()
                    .filter(segmentPlan -> !segmentPlan.isNew())
                    .toList();
            if (retained.stream().noneMatch(value ->
                    value.entity().getSequenceNumber() != value.request().sequenceNumber())) {
                continue;
            }
            Set<Integer> reserved = new HashSet<>();
            retained.forEach(value -> reserved.add(value.entity().getSequenceNumber()));
            retained.forEach(value -> reserved.add(value.request().sequenceNumber()));
            int[] temporarySequences = temporarySequences(reserved, retained.size());
            for (int index = 0; index < retained.size(); index++) {
                TrainingSegment entity = retained.get(index).entity();
                entity.update(temporarySequences[index], entity.getDistanceM(), entity.getRestAfterSeconds());
            }
        }
        segmentRepository.flush();
    }

    private int[] temporarySequences(Set<Integer> reserved, int count) {
        int[] values = new int[count];
        long candidate = 1;
        for (int index = 0; index < count; index++) {
            while (candidate <= Integer.MAX_VALUE && reserved.contains((int) candidate)) {
                candidate++;
            }
            if (candidate > Integer.MAX_VALUE) {
                throw new InvalidTrainingRequestException("No temporary order values are available");
            }
            values[index] = (int) candidate;
            reserved.add(values[index]);
            candidate++;
        }
        return values;
    }

    private <T> Map<Long, T> indexById(List<T> entities, IdFunction<T> idFunction) {
        Map<Long, T> result = new HashMap<>();
        for (T entity : entities) {
            result.put(idFunction.id(entity), entity);
        }
        return result;
    }

    private Set<Long> difference(Set<Long> allIds, Set<Long> retainedIds) {
        Set<Long> removed = new HashSet<>(allIds);
        removed.removeAll(retainedIds);
        return removed;
    }

    private interface IdFunction<T> {
        Long id(T value);
    }

    private record DefinitionPlan(
            EditTrainingDefinitionRequest request,
            TrainingDefinition entity,
            boolean isNew,
            List<SegmentPlan> segments) {
    }

    private record SegmentPlan(EditTrainingSegmentRequest request, TrainingSegment entity, boolean isNew) {
    }

    private record EditPlan(
            List<DefinitionPlan> definitions,
            Set<Long> removedDefinitionIds,
            Set<Long> removedSegmentIds) {
    }

    private record ExecutionPair(long participationId, long segmentId) {
    }
}

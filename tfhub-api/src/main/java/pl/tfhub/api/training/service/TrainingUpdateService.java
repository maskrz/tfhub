package pl.tfhub.api.training.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.tfhub.api.athlete.entity.Athlete;
import pl.tfhub.api.athlete.exception.AthleteNotFoundException;
import pl.tfhub.api.athlete.repository.AthleteRepository;
import pl.tfhub.api.common.authorization.AccessScopeService;
import pl.tfhub.api.training.dto.request.EditTrainingDefinitionRequest;
import pl.tfhub.api.training.dto.request.EditTrainingExecutionRequest;
import pl.tfhub.api.training.dto.request.EditTrainingParticipationRequest;
import pl.tfhub.api.training.dto.request.EditTrainingRequest;
import pl.tfhub.api.training.dto.request.EditTrainingSegmentRequest;
import pl.tfhub.api.training.dto.response.UpdateTrainingResponse;
import pl.tfhub.api.training.entity.AthleteTrainingParticipation;
import pl.tfhub.api.training.entity.Training;
import pl.tfhub.api.training.entity.TrainingDefinition;
import pl.tfhub.api.training.entity.TrainingSegment;
import pl.tfhub.api.training.entity.TrainingSegmentExecution;
import pl.tfhub.api.training.enums.TrainingExecutionStatus;
import pl.tfhub.api.training.exception.InactiveTrainingException;
import pl.tfhub.api.training.exception.InvalidTrainingIdException;
import pl.tfhub.api.training.exception.InvalidTrainingRequestException;
import pl.tfhub.api.training.exception.TrainingNotFoundException;
import pl.tfhub.api.training.repository.AthleteTrainingParticipationRepository;
import pl.tfhub.api.training.repository.TrainingCreationRepository;
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
    private final AthleteRepository athleteRepository;
    private final TrainingCreationRepository trainingCreationRepository;
    private final AccessScopeService accessScopeService;

    public TrainingUpdateService(
            TrainingRepository trainingRepository,
            TrainingDefinitionRepository definitionRepository,
            TrainingSegmentRepository segmentRepository,
            AthleteTrainingParticipationRepository participationRepository,
            TrainingSegmentExecutionRepository executionRepository,
            AthleteRepository athleteRepository,
            TrainingCreationRepository trainingCreationRepository,
            AccessScopeService accessScopeService) {
        this.trainingRepository = trainingRepository;
        this.definitionRepository = definitionRepository;
        this.segmentRepository = segmentRepository;
        this.participationRepository = participationRepository;
        this.executionRepository = executionRepository;
        this.athleteRepository = athleteRepository;
        this.trainingCreationRepository = trainingCreationRepository;
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
        if (!training.isActive()) {
            throw new InactiveTrainingException(trainingId);
        }
        if (request.trainingGroupId() != null
                && !request.trainingGroupId().equals(training.getTrainingGroup().getId())) {
            throw new InvalidTrainingRequestException("A training's group cannot be changed");
        }

        List<TrainingDefinition> currentDefinitions =
                definitionRepository.findAllByTraining_IdOrderBySequenceNumberAsc(trainingId);
        Map<Long, TrainingDefinition> definitionsById = indexById(currentDefinitions, TrainingDefinition::getId);
        List<Long> definitionIds = currentDefinitions.stream().map(TrainingDefinition::getId).toList();
        List<TrainingSegment> currentSegments = definitionIds.isEmpty()
                ? List.of()
                : segmentRepository
                        .findAllByTrainingDefinition_IdInOrderByTrainingDefinition_IdAscSequenceNumberAsc(
                                definitionIds);
        Map<Long, TrainingSegment> segmentsById = indexById(currentSegments, TrainingSegment::getId);

        List<AthleteTrainingParticipation> currentParticipations =
                participationRepository.findAllByTraining_IdOrderByTrainingDefinition_IdAscIdAsc(trainingId);
        Map<Long, AthleteTrainingParticipation> participationsById =
                indexById(currentParticipations, AthleteTrainingParticipation::getId);
        List<Long> participationIds = currentParticipations.stream()
                .map(AthleteTrainingParticipation::getId)
                .toList();
        List<TrainingSegmentExecution> currentExecutions = participationIds.isEmpty()
                ? List.of()
                : executionRepository.findAllByParticipation_IdIn(participationIds);
        Map<Long, TrainingSegmentExecution> executionsById =
                indexById(currentExecutions, TrainingSegmentExecution::getId);

        EditPlan plan = validateAndPlan(
                request, training, definitionsById, segmentsById, participationsById, executionsById);
        validateNewAthletes(plan.newAthleteIds(), training.getTrainingGroup().getId());
        validateRemovalRestrictions(plan, currentExecutions);

        applyPlan(
                training, request, plan, currentDefinitions, currentSegments, currentParticipations, currentExecutions);
        return new UpdateTrainingResponse(trainingId);
    }

    private EditPlan validateAndPlan(
            EditTrainingRequest request,
            Training training,
            Map<Long, TrainingDefinition> definitionsById,
            Map<Long, TrainingSegment> segmentsById,
            Map<Long, AthleteTrainingParticipation> participationsById,
            Map<Long, TrainingSegmentExecution> executionsById) {
        Set<Integer> definitionSequences = new HashSet<>();
        Set<Long> retainedDefinitionIds = new HashSet<>();
        Set<Long> retainedSegmentIds = new HashSet<>();
        Set<Long> retainedParticipationIds = new HashSet<>();
        Set<Long> athleteIds = new HashSet<>();
        Set<Long> executionUpdateIds = new HashSet<>();
        List<Long> newAthleteIds = new ArrayList<>();
        List<DefinitionPlan> definitionPlans = new ArrayList<>();

        for (EditTrainingDefinitionRequest definitionRequest : request.definitions()) {
            if (!definitionSequences.add(definitionRequest.sequenceNumber())) {
                throw new InvalidTrainingRequestException("Definition sequence numbers must be unique");
            }
            TrainingDefinition definition;
            boolean newDefinition = definitionRequest.id() == null;
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
            Set<Long> definitionSegmentIds = new HashSet<>();
            for (EditTrainingSegmentRequest segmentRequest : definitionRequest.segments()) {
                if (!segmentSequences.add(segmentRequest.sequenceNumber())) {
                    throw new InvalidTrainingRequestException(
                            "Segment sequence numbers must be unique within each definition");
                }
                TrainingSegment segment;
                boolean newSegment = segmentRequest.id() == null;
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
                    definitionSegmentIds.add(segmentRequest.id());
                }
                segmentPlans.add(new SegmentPlan(segmentRequest, segment, newSegment));
            }

            List<ParticipationPlan> participationPlans = new ArrayList<>();
            for (EditTrainingParticipationRequest participationRequest : definitionRequest.participations()) {
                AthleteTrainingParticipation participation;
                boolean newParticipation = participationRequest.id() == null;
                if (newParticipation) {
                    participation = null;
                    newAthleteIds.add(participationRequest.athleteId());
                } else {
                    participation = participationsById.get(participationRequest.id());
                    if (participation == null
                            || !participation.getTrainingDefinition().getId().equals(definition.getId())
                            || !participation.getAthlete().getId().equals(participationRequest.athleteId())
                            || !retainedParticipationIds.add(participationRequest.id())) {
                        throw new InvalidTrainingRequestException(
                                "A participation ID is invalid, duplicated, or cannot be moved between definitions");
                    }
                }
                if (!athleteIds.add(participationRequest.athleteId())) {
                    throw new InvalidTrainingRequestException(
                            "An athlete may participate in only one definition in a training");
                }

                List<EditTrainingExecutionRequest> executionUpdates =
                        participationRequest.executionUpdates() == null
                                ? List.of()
                                : participationRequest.executionUpdates();
                for (EditTrainingExecutionRequest executionRequest : executionUpdates) {
                    TrainingSegmentExecution execution = executionsById.get(executionRequest.id());
                    if (newParticipation
                            || execution == null
                            || !execution.getParticipation().getId().equals(participationRequest.id())
                            || !definitionSegmentIds.contains(execution.getTrainingSegment().getId())
                            || !executionUpdateIds.add(executionRequest.id())) {
                        throw new InvalidTrainingRequestException(
                                "An execution ID is invalid, duplicated, or outside this participation");
                    }
                    validateExecutionUpdate(executionRequest);
                }
                participationPlans.add(new ParticipationPlan(
                        participationRequest, participation, newParticipation));
            }
            definitionPlans.add(new DefinitionPlan(
                    definitionRequest, definition, newDefinition, segmentPlans, participationPlans));
        }

        Set<Long> removedDefinitionIds = difference(definitionsById.keySet(), retainedDefinitionIds);
        Set<Long> removedSegmentIds = difference(segmentsById.keySet(), retainedSegmentIds);
        Set<Long> removedParticipationIds = difference(participationsById.keySet(), retainedParticipationIds);
        return new EditPlan(
                definitionPlans,
                removedDefinitionIds,
                removedSegmentIds,
                removedParticipationIds,
                newAthleteIds);
    }

    private void validateNewAthletes(List<Long> newAthleteIds, long trainingGroupId) {
        if (newAthleteIds.isEmpty()) {
            return;
        }
        Map<Long, Long> athleteGroups = new HashMap<>();
        for (TrainingCreationRepository.AthleteTrainingGroup row
                : trainingCreationRepository.findAthleteTrainingGroups(newAthleteIds)) {
            athleteGroups.put(row.athleteId(), row.trainingGroupId());
        }
        for (Long athleteId : newAthleteIds) {
            if (!athleteGroups.containsKey(athleteId)) {
                throw new AthleteNotFoundException(athleteId);
            }
            if (!Long.valueOf(trainingGroupId).equals(athleteGroups.get(athleteId))) {
                throw new InvalidTrainingRequestException(
                        "Every newly assigned athlete must currently belong to the training group");
            }
        }
    }

    private void validateRemovalRestrictions(EditPlan plan, List<TrainingSegmentExecution> executions) {
        Set<Long> recordedSegmentIds = new HashSet<>();
        for (TrainingSegmentExecution execution : executions) {
            if (execution.getStatus() != TrainingExecutionStatus.RECORDED) {
                continue;
            }
            long definitionId = execution.getTrainingDefinition().getId();
            long segmentId = execution.getTrainingSegment().getId();
            long participationId = execution.getParticipation().getId();
            recordedSegmentIds.add(segmentId);
            if (plan.removedDefinitionIds().contains(definitionId)) {
                throw new InvalidTrainingRequestException(
                        "A definition with recorded execution data cannot be removed");
            }
            if (plan.removedSegmentIds().contains(segmentId)) {
                throw new InvalidTrainingRequestException(
                        "A segment with recorded execution data cannot be removed");
            }
            if (plan.removedParticipationIds().contains(participationId)) {
                throw new InvalidTrainingRequestException(
                        "A participation with recorded execution data cannot be removed");
            }
        }

        for (DefinitionPlan definitionPlan : plan.definitions()) {
            for (SegmentPlan segmentPlan : definitionPlan.segments()) {
                if (segmentPlan.isNew()) {
                    continue;
                }
                long segmentId = segmentPlan.entity().getId();
                if (recordedSegmentIds.contains(segmentId)
                        && segmentPlan.entity().getDistanceM() != segmentPlan.request().distanceM()) {
                    throw new InvalidTrainingRequestException(
                            "The distance of a segment with recorded execution data cannot be changed");
                }
            }
        }
    }

    private void validateExecutionUpdate(EditTrainingExecutionRequest request) {
        if (request.status() == null || request.usesSpikes() == null) {
            throw new InvalidTrainingRequestException("Execution status and spike usage are required");
        }
        if ((request.status() == TrainingExecutionStatus.RECORDED) != (request.measuredResult() != null)) {
            throw new InvalidTrainingRequestException(
                    "A measured result is required only for RECORDED executions");
        }
        if (request.measuredResult() != null && request.measuredResult().signum() < 0) {
            throw new InvalidTrainingRequestException("Measured result must not be negative");
        }
    }

    private void applyPlan(
            Training training,
            EditTrainingRequest request,
            EditPlan plan,
            List<TrainingDefinition> currentDefinitions,
            List<TrainingSegment> currentSegments,
            List<AthleteTrainingParticipation> currentParticipations,
            List<TrainingSegmentExecution> currentExecutions) {
        deleteRemovedRows(
                plan, currentDefinitions, currentSegments, currentParticipations, currentExecutions);
        training.updateDetails(request.name(), request.trainingDate());

        List<DefinitionPlan> retainedDefinitions = plan.definitions().stream()
                .filter(definitionPlan -> !definitionPlan.isNew())
                .toList();
        if (retainedDefinitions.stream().anyMatch(definitionPlan ->
                definitionPlan.entity().getSequenceNumber() != definitionPlan.request().sequenceNumber())) {
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

        List<AthleteTrainingParticipation> newParticipations = new ArrayList<>();
        for (DefinitionPlan definitionPlan : plan.definitions()) {
            for (ParticipationPlan participationPlan : definitionPlan.participations()) {
                if (participationPlan.isNew()) {
                    Athlete athlete = athleteRepository.getReferenceById(
                            participationPlan.request().athleteId());
                    participationPlan.setEntity(new AthleteTrainingParticipation(
                            training, definitionPlan.entity(), athlete));
                    newParticipations.add(participationPlan.entity());
                }
            }
        }
        participationRepository.saveAllAndFlush(newParticipations);

        Map<Long, TrainingSegmentExecution> executionsById =
                indexById(currentExecutions, TrainingSegmentExecution::getId);
        for (DefinitionPlan definitionPlan : plan.definitions()) {
            for (ParticipationPlan participationPlan : definitionPlan.participations()) {
                for (EditTrainingExecutionRequest executionRequest : participationPlan.executionUpdates()) {
                    TrainingSegmentExecution execution = executionsById.get(executionRequest.id());
                    execution.update(
                            executionRequest.status(),
                            executionRequest.measuredResult(),
                            executionRequest.usesSpikes());
                }
            }
        }

        Set<ExecutionPair> existingPairs = new HashSet<>();
        for (TrainingSegmentExecution execution : currentExecutions) {
            if (!plan.removedParticipationIds().contains(execution.getParticipation().getId())
                    && !plan.removedSegmentIds().contains(execution.getTrainingSegment().getId())) {
                existingPairs.add(new ExecutionPair(
                        execution.getParticipation().getId(), execution.getTrainingSegment().getId()));
            }
        }
        List<TrainingSegmentExecution> newExecutions = new ArrayList<>();
        for (DefinitionPlan definitionPlan : plan.definitions()) {
            for (ParticipationPlan participationPlan : definitionPlan.participations()) {
                AthleteTrainingParticipation participation = participationPlan.entity();
                for (SegmentPlan segmentPlan : definitionPlan.segments()) {
                    TrainingSegment segment = segmentPlan.entity();
                    ExecutionPair pair = new ExecutionPair(participation.getId(), segment.getId());
                    if (existingPairs.add(pair)) {
                        newExecutions.add(new TrainingSegmentExecution(
                                participation,
                                segment,
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
                        plan.removedParticipationIds().contains(execution.getParticipation().getId())
                                || plan.removedSegmentIds().contains(execution.getTrainingSegment().getId()))
                .toList();
        executionRepository.deleteAll(executionsToDelete);
        executionRepository.flush();

        List<AthleteTrainingParticipation> participationsToDelete = participations.stream()
                .filter(participation -> plan.removedParticipationIds().contains(participation.getId()))
                .toList();
        participationRepository.deleteAll(participationsToDelete);
        participationRepository.flush();

        Set<Long> removedSegmentIds = plan.removedSegmentIds();
        List<TrainingSegment> segmentsToDelete = segments.stream()
                .filter(segment -> removedSegmentIds.contains(segment.getId()))
                .toList();
        segmentRepository.deleteAll(segmentsToDelete);
        segmentRepository.flush();

        List<TrainingDefinition> definitionsToDelete = definitions.stream()
                .filter(definition -> plan.removedDefinitionIds().contains(definition.getId()))
                .toList();
        definitionRepository.deleteAll(definitionsToDelete);
        definitionRepository.flush();
    }

    private void stageSegmentSequences(List<DefinitionPlan> definitionPlans) {
        for (DefinitionPlan definitionPlan : definitionPlans) {
            List<SegmentPlan> retained = definitionPlan.segments().stream()
                    .filter(segmentPlan -> !segmentPlan.isNew())
                    .toList();
            if (retained.stream().noneMatch(segmentPlan ->
                    segmentPlan.entity().getSequenceNumber() != segmentPlan.request().sequenceNumber())) {
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
            List<SegmentPlan> segments,
            List<ParticipationPlan> participations) {
    }

    private record SegmentPlan(EditTrainingSegmentRequest request, TrainingSegment entity, boolean isNew) {
    }

    private static final class ParticipationPlan {
        private final EditTrainingParticipationRequest request;
        private AthleteTrainingParticipation entity;
        private final boolean isNew;

        private ParticipationPlan(
                EditTrainingParticipationRequest request,
                AthleteTrainingParticipation entity,
                boolean isNew) {
            this.request = request;
            this.entity = entity;
            this.isNew = isNew;
        }

        private EditTrainingParticipationRequest request() {
            return request;
        }

        private AthleteTrainingParticipation entity() {
            return entity;
        }

        private void setEntity(AthleteTrainingParticipation entity) {
            this.entity = entity;
        }

        private boolean isNew() {
            return isNew;
        }

        private List<EditTrainingExecutionRequest> executionUpdates() {
            return request.executionUpdates() == null ? List.of() : request.executionUpdates();
        }
    }

    private record EditPlan(
            List<DefinitionPlan> definitions,
            Set<Long> removedDefinitionIds,
            Set<Long> removedSegmentIds,
            Set<Long> removedParticipationIds,
            List<Long> newAthleteIds) {
    }

    private record ExecutionPair(long participationId, long segmentId) {
    }
}

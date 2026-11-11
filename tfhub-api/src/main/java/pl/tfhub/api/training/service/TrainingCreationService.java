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
import pl.tfhub.api.training.dto.request.CreateTrainingDefinitionRequest;
import pl.tfhub.api.training.dto.request.CreateTrainingRequest;
import pl.tfhub.api.training.dto.response.CreateTrainingResponse;
import pl.tfhub.api.training.entity.AthleteTrainingParticipation;
import pl.tfhub.api.training.entity.Training;
import pl.tfhub.api.training.entity.TrainingDefinition;
import pl.tfhub.api.training.entity.TrainingSegment;
import pl.tfhub.api.training.entity.TrainingSegmentExecution;
import pl.tfhub.api.training.enums.TrainingExecutionStatus;
import pl.tfhub.api.training.exception.InvalidTrainingRequestException;
import pl.tfhub.api.training.repository.AthleteTrainingParticipationRepository;
import pl.tfhub.api.training.repository.TrainingCreationRepository;
import pl.tfhub.api.training.repository.TrainingDefinitionRepository;
import pl.tfhub.api.training.repository.TrainingRepository;
import pl.tfhub.api.training.repository.TrainingSegmentExecutionRepository;
import pl.tfhub.api.training.repository.TrainingSegmentRepository;
import pl.tfhub.api.traininggroup.entity.TrainingGroup;
import pl.tfhub.api.traininggroup.exception.TrainingGroupNotFoundException;
import pl.tfhub.api.traininggroup.repository.TrainingGroupRepository;

@Service
public class TrainingCreationService {

    private final TrainingRepository trainingRepository;
    private final TrainingDefinitionRepository definitionRepository;
    private final TrainingSegmentRepository segmentRepository;
    private final AthleteTrainingParticipationRepository participationRepository;
    private final TrainingSegmentExecutionRepository executionRepository;
    private final TrainingGroupRepository trainingGroupRepository;
    private final AthleteRepository athleteRepository;
    private final TrainingCreationRepository trainingCreationRepository;
    private final AccessScopeService accessScopeService;

    public TrainingCreationService(
            TrainingRepository trainingRepository,
            TrainingDefinitionRepository definitionRepository,
            TrainingSegmentRepository segmentRepository,
            AthleteTrainingParticipationRepository participationRepository,
            TrainingSegmentExecutionRepository executionRepository,
            TrainingGroupRepository trainingGroupRepository,
            AthleteRepository athleteRepository,
            TrainingCreationRepository trainingCreationRepository,
            AccessScopeService accessScopeService) {
        this.trainingRepository = trainingRepository;
        this.definitionRepository = definitionRepository;
        this.segmentRepository = segmentRepository;
        this.participationRepository = participationRepository;
        this.executionRepository = executionRepository;
        this.trainingGroupRepository = trainingGroupRepository;
        this.athleteRepository = athleteRepository;
        this.trainingCreationRepository = trainingCreationRepository;
        this.accessScopeService = accessScopeService;
    }

    @Transactional
    public CreateTrainingResponse createTraining(CreateTrainingRequest request) {
        accessScopeService.requireCoachOrAdmin();

        TrainingGroup trainingGroup = trainingGroupRepository.findById(request.trainingGroupId())
                .orElseThrow(() -> new TrainingGroupNotFoundException(request.trainingGroupId()));
        accessScopeService.requireGroupAssignment(request.trainingGroupId());

        List<Long> athleteIds = validateRequest(request);
        Map<Long, Athlete> athletes = new HashMap<>();
        validateAthletes(athleteIds, request.trainingGroupId());
        for (Long athleteId : athleteIds) {
            athletes.put(athleteId, athleteRepository.getReferenceById(athleteId));
        }

        Training training = trainingRepository.saveAndFlush(
                new Training(request.name(), request.trainingDate(), trainingGroup));

        List<DefinitionPlan> plans = new ArrayList<>();
        List<TrainingDefinition> definitions = new ArrayList<>();
        for (CreateTrainingDefinitionRequest definitionRequest : request.definitions()) {
            TrainingDefinition definition = new TrainingDefinition(
                    training, definitionRequest.name(), definitionRequest.sequenceNumber());
            definitions.add(definition);
            plans.add(new DefinitionPlan(definitionRequest, definition));
        }
        definitionRepository.saveAllAndFlush(definitions);

        List<TrainingSegment> segments = new ArrayList<>();
        for (DefinitionPlan plan : plans) {
            for (var segmentRequest : plan.request().segments()) {
                TrainingSegment segment = new TrainingSegment(
                        plan.definition(),
                        segmentRequest.sequenceNumber(),
                        segmentRequest.distanceM(),
                        segmentRequest.restAfterSeconds());
                plan.segments().add(segment);
                segments.add(segment);
            }
        }
        segmentRepository.saveAllAndFlush(segments);

        List<AthleteTrainingParticipation> participations = new ArrayList<>();
        List<ParticipationPlan> participationPlans = new ArrayList<>();
        for (DefinitionPlan plan : plans) {
            List<Long> definitionAthleteIds = plan.request().athleteIds() == null
                    ? List.of()
                    : plan.request().athleteIds();
            for (Long athleteId : definitionAthleteIds) {
                AthleteTrainingParticipation participation = new AthleteTrainingParticipation(
                        training, plan.definition(), athletes.get(athleteId));
                participations.add(participation);
                participationPlans.add(new ParticipationPlan(participation, plan.segments()));
            }
        }
        participationRepository.saveAllAndFlush(participations);

        List<TrainingSegmentExecution> executions = new ArrayList<>();
        for (ParticipationPlan plan : participationPlans) {
            for (TrainingSegment segment : plan.segments()) {
                executions.add(new TrainingSegmentExecution(
                        plan.participation(),
                        segment,
                        plan.participation().getTrainingDefinition(),
                        TrainingExecutionStatus.PENDING,
                        null,
                        false));
            }
        }
        executionRepository.saveAll(executions);

        return new CreateTrainingResponse(training.getId());
    }

    private List<Long> validateRequest(CreateTrainingRequest request) {
        Set<Integer> definitionSequences = new HashSet<>();
        Set<Long> assignedAthleteIds = new HashSet<>();
        List<Long> athleteIds = new ArrayList<>();

        for (CreateTrainingDefinitionRequest definition : request.definitions()) {
            if (!definitionSequences.add(definition.sequenceNumber())) {
                throw new InvalidTrainingRequestException("Definition sequence numbers must be unique");
            }

            Set<Integer> segmentSequences = new HashSet<>();
            for (var segment : definition.segments()) {
                if (!segmentSequences.add(segment.sequenceNumber())) {
                    throw new InvalidTrainingRequestException(
                            "Segment sequence numbers must be unique within each definition");
                }
            }

            if (definition.athleteIds() == null) {
                continue;
            }
            for (Long athleteId : definition.athleteIds()) {
                if (!assignedAthleteIds.add(athleteId)) {
                    throw new InvalidTrainingRequestException(
                            "An athlete may be assigned to only one definition in a training");
                }
                athleteIds.add(athleteId);
            }
        }
        return athleteIds;
    }

    private void validateAthletes(List<Long> athleteIds, long trainingGroupId) {
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
                        "Every assigned athlete must currently belong to the selected training group");
            }
        }
    }

    private record DefinitionPlan(
            CreateTrainingDefinitionRequest request,
            TrainingDefinition definition,
            List<TrainingSegment> segments) {
        private DefinitionPlan(CreateTrainingDefinitionRequest request, TrainingDefinition definition) {
            this(request, definition, new ArrayList<>());
        }
    }

    private record ParticipationPlan(
            AthleteTrainingParticipation participation,
            List<TrainingSegment> segments) {
    }
}

package pl.tfhub.api.training.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.tfhub.api.common.authorization.AccessScopeService;
import pl.tfhub.api.training.dto.request.CreateTrainingDefinitionRequest;
import pl.tfhub.api.training.dto.request.CreateTrainingRequest;
import pl.tfhub.api.training.dto.response.CreateTrainingResponse;
import pl.tfhub.api.training.entity.Training;
import pl.tfhub.api.training.entity.TrainingDefinition;
import pl.tfhub.api.training.entity.TrainingSegment;
import pl.tfhub.api.training.exception.InvalidTrainingRequestException;
import pl.tfhub.api.training.repository.TrainingDefinitionRepository;
import pl.tfhub.api.training.repository.TrainingRepository;
import pl.tfhub.api.training.repository.TrainingSegmentRepository;
import pl.tfhub.api.traininggroup.entity.TrainingGroup;
import pl.tfhub.api.traininggroup.exception.TrainingGroupNotFoundException;
import pl.tfhub.api.traininggroup.repository.TrainingGroupRepository;

@Service
public class TrainingCreationService {

    private final TrainingRepository trainingRepository;
    private final TrainingDefinitionRepository definitionRepository;
    private final TrainingSegmentRepository segmentRepository;
    private final TrainingGroupRepository trainingGroupRepository;
    private final AccessScopeService accessScopeService;

    public TrainingCreationService(
            TrainingRepository trainingRepository,
            TrainingDefinitionRepository definitionRepository,
            TrainingSegmentRepository segmentRepository,
            TrainingGroupRepository trainingGroupRepository,
            AccessScopeService accessScopeService) {
        this.trainingRepository = trainingRepository;
        this.definitionRepository = definitionRepository;
        this.segmentRepository = segmentRepository;
        this.trainingGroupRepository = trainingGroupRepository;
        this.accessScopeService = accessScopeService;
    }

    @Transactional
    public CreateTrainingResponse createTraining(CreateTrainingRequest request) {
        accessScopeService.requireCoachOrAdmin();

        TrainingGroup trainingGroup = trainingGroupRepository.findById(request.trainingGroupId())
                .orElseThrow(() -> new TrainingGroupNotFoundException(request.trainingGroupId()));
        accessScopeService.requireGroupAssignment(request.trainingGroupId());
        validateDefinitionAndSegmentOrders(request);

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
                segments.add(segment);
            }
        }
        segmentRepository.saveAllAndFlush(segments);

        return new CreateTrainingResponse(training.getId());
    }

    private void validateDefinitionAndSegmentOrders(CreateTrainingRequest request) {
        Set<Integer> definitionOrders = new HashSet<>();
        for (CreateTrainingDefinitionRequest definition : request.definitions()) {
            if (!definitionOrders.add(definition.sequenceNumber())) {
                throw new InvalidTrainingRequestException(
                        "Training definition sequence numbers must be unique");
            }
            Set<Integer> segmentOrders = new java.util.HashSet<>();
            for (var segment : definition.segments()) {
                if (!segmentOrders.add(segment.sequenceNumber())) {
                    throw new InvalidTrainingRequestException(
                            "Training segment sequence numbers must be unique within a definition");
                }
            }
        }
    }

    private record DefinitionPlan(CreateTrainingDefinitionRequest request, TrainingDefinition definition) {
    }

}

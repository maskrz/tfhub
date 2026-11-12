package pl.tfhub.api.training.service;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.tfhub.api.common.authorization.AccessScopeService;
import pl.tfhub.api.training.entity.AthleteTrainingParticipation;
import pl.tfhub.api.training.entity.Training;
import pl.tfhub.api.training.entity.TrainingDefinition;
import pl.tfhub.api.training.entity.TrainingSegment;
import pl.tfhub.api.training.entity.TrainingSegmentExecution;
import pl.tfhub.api.training.exception.InvalidTrainingIdException;
import pl.tfhub.api.training.exception.TrainingNotFoundException;
import pl.tfhub.api.training.repository.AthleteTrainingParticipationRepository;
import pl.tfhub.api.training.repository.TrainingDefinitionRepository;
import pl.tfhub.api.training.repository.TrainingRepository;
import pl.tfhub.api.training.repository.TrainingSegmentExecutionRepository;
import pl.tfhub.api.training.repository.TrainingSegmentRepository;
import pl.tfhub.api.user.enums.UserRole;

@Service
public class TrainingDeletionService {

    private final TrainingRepository trainingRepository;
    private final TrainingDefinitionRepository definitionRepository;
    private final TrainingSegmentRepository segmentRepository;
    private final AthleteTrainingParticipationRepository participationRepository;
    private final TrainingSegmentExecutionRepository executionRepository;
    private final AccessScopeService accessScopeService;

    public TrainingDeletionService(
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
    public void deleteTraining(long trainingId) {
        accessScopeService.requireCoachOrAdmin();
        if (trainingId <= 0) {
            throw new InvalidTrainingIdException();
        }

        Training training = trainingRepository.findByIdForUpdate(trainingId)
                .orElseThrow(() -> new TrainingNotFoundException(trainingId));
        if (accessScopeService.currentUser().role() == UserRole.COACH
                && !accessScopeService.assignedGroupIds().contains(training.getTrainingGroup().getId())) {
            throw new TrainingNotFoundException(trainingId);
        }

        List<TrainingDefinition> definitions =
                definitionRepository.findAllByTraining_IdOrderBySequenceNumberAsc(trainingId);
        List<Long> definitionIds = definitions.stream().map(TrainingDefinition::getId).toList();
        List<TrainingSegment> segments = definitionIds.isEmpty()
                ? List.of()
                : segmentRepository
                        .findAllByTrainingDefinition_IdInOrderByTrainingDefinition_IdAscSequenceNumberAsc(
                                definitionIds);
        List<AthleteTrainingParticipation> participations =
                participationRepository.findAllByTraining_IdOrderByTrainingDefinition_IdAscIdAsc(trainingId);
        List<Long> participationIds = participations.stream()
                .map(AthleteTrainingParticipation::getId)
                .toList();
        List<TrainingSegmentExecution> executions = participationIds.isEmpty()
                ? List.of()
                : executionRepository.findAllByParticipation_IdIn(participationIds);

        executionRepository.deleteAll(executions);
        executionRepository.flush();
        participationRepository.deleteAll(participations);
        participationRepository.flush();
        segmentRepository.deleteAll(segments);
        segmentRepository.flush();
        definitionRepository.deleteAll(definitions);
        definitionRepository.flush();
        trainingRepository.delete(training);
        trainingRepository.flush();
    }
}

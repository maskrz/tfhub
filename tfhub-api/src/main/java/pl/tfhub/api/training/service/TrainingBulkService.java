package pl.tfhub.api.training.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.tfhub.api.common.authorization.AccessScopeService;
import pl.tfhub.api.training.dto.request.CompleteTrainingRequest;
import pl.tfhub.api.training.dto.response.BulkTrainingCompletionResponse;
import pl.tfhub.api.training.entity.Training;
import pl.tfhub.api.training.enums.TrainingStatus;
import pl.tfhub.api.training.enums.TrainingType;
import pl.tfhub.api.training.exception.InvalidTrainingRequestException;
import pl.tfhub.api.training.exception.TrainingNotFoundException;
import pl.tfhub.api.training.repository.TrainingAssignmentRepository;
import pl.tfhub.api.training.repository.TrainingRepository;
import pl.tfhub.api.user.enums.UserRole;

@Service
public class TrainingBulkService {
    private final TrainingRepository trainingRepository;
    private final TrainingAssignmentRepository assignmentRepository;
    private final AccessScopeService accessScopeService;
    private final TrainingDeletionService deletionService;
    private final TrainingCompletionService completionService;
    private final FreeTextTrainingService freeTextService;

    public TrainingBulkService(TrainingRepository trainingRepository, TrainingAssignmentRepository assignmentRepository,
            AccessScopeService accessScopeService, TrainingDeletionService deletionService,
            TrainingCompletionService completionService, FreeTextTrainingService freeTextService) {
        this.trainingRepository = trainingRepository;
        this.assignmentRepository = assignmentRepository;
        this.accessScopeService = accessScopeService;
        this.deletionService = deletionService;
        this.completionService = completionService;
        this.freeTextService = freeTextService;
    }

    public static List<Long> validatedIds(List<Long> ids) {
        if (ids == null || ids.isEmpty() || ids.stream().anyMatch(id -> id == null || id <= 0)
                || new HashSet<>(ids).size() != ids.size()) {
            throw new InvalidTrainingRequestException("Training IDs must be positive, unique and non-empty");
        }
        return ids.stream().sorted().toList();
    }

    @Transactional
    public void delete(List<Long> ids) {
        lockAndAuthorize(ids);
        for (Long id : validatedIds(ids)) {
            deletionService.deleteTraining(id);
        }
    }

    @Transactional
    public BulkTrainingCompletionResponse complete(List<Long> ids) {
        List<Training> trainings = lockAndAuthorize(ids);
        List<Long> completed = new ArrayList<>();
        List<Long> alreadyCompleted = new ArrayList<>();
        List<Long> undated = new ArrayList<>();
        for (Training training : trainings) {
            long id = training.getId();
            if (training.getStatus() == TrainingStatus.COMPLETED) {
                alreadyCompleted.add(id);
            } else if (training.getTrainingDate() == null) {
                undated.add(id);
            } else {
                if (training.getTrainingType() == TrainingType.TEMPO) {
                    completionService.completeTraining(id, new CompleteTrainingRequest(List.of(), List.of(), List.of()));
                } else {
                    freeTextService.markCompleted(id);
                }
                completed.add(id);
            }
        }
        return new BulkTrainingCompletionResponse(completed, alreadyCompleted, undated);
    }

    private List<Training> lockAndAuthorize(List<Long> ids) {
        accessScopeService.requireCoachOrAdmin();
        var user = accessScopeService.currentUser();
        List<Training> trainings = new ArrayList<>();
        for (Long id : validatedIds(ids)) {
            Training training = trainingRepository.findByIdForUpdate(id)
                    .orElseThrow(() -> new TrainingNotFoundException(id));
            if (user.role() == UserRole.COACH && !assignmentRepository.canCoachManage(id, user.id())) {
                throw new TrainingNotFoundException(id);
            }
            trainings.add(training);
        }
        return trainings;
    }
}

package pl.tfhub.api.training.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.tfhub.api.common.authorization.AccessScopeService;
import pl.tfhub.api.training.dto.response.DeactivateTrainingResponse;
import pl.tfhub.api.training.entity.Training;
import pl.tfhub.api.training.exception.InactiveTrainingException;
import pl.tfhub.api.training.exception.InvalidTrainingIdException;
import pl.tfhub.api.training.exception.TrainingNotFoundException;
import pl.tfhub.api.training.repository.TrainingRepository;
import pl.tfhub.api.user.enums.UserRole;

@Service
public class TrainingDeactivationService {

    private final TrainingRepository trainingRepository;
    private final AccessScopeService accessScopeService;

    public TrainingDeactivationService(
            TrainingRepository trainingRepository,
            AccessScopeService accessScopeService) {
        this.trainingRepository = trainingRepository;
        this.accessScopeService = accessScopeService;
    }

    @Transactional
    public DeactivateTrainingResponse deactivateTraining(long trainingId) {
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
        if (!training.isActive()) {
            throw new InactiveTrainingException(trainingId);
        }

        training.setActive(false);
        trainingRepository.saveAndFlush(training);
        return new DeactivateTrainingResponse(trainingId);
    }
}

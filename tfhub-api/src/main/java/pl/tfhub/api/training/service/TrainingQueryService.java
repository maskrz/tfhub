package pl.tfhub.api.training.service;

import java.time.YearMonth;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;
import pl.tfhub.api.common.authorization.AccessScopeService;
import pl.tfhub.api.training.dto.response.TrainingListItemResponse;
import pl.tfhub.api.training.enums.TrainingType;
import pl.tfhub.api.training.repository.TrainingListRepository;
import pl.tfhub.api.user.enums.UserRole;

@Service
public class TrainingQueryService {

    private final TrainingListRepository trainingListRepository;
    private final AccessScopeService accessScopeService;

    public TrainingQueryService(
            TrainingListRepository trainingListRepository,
            AccessScopeService accessScopeService) {
        this.trainingListRepository = trainingListRepository;
        this.accessScopeService = accessScopeService;
    }

    @Transactional(readOnly = true)
    public List<TrainingListItemResponse> listTrainings(boolean includeCompleted, TrainingType trainingType) {
        return listTrainings(includeCompleted, trainingType, null, false);
    }

    @Transactional(readOnly = true)
    public List<TrainingListItemResponse> listTrainings(
            boolean includeCompleted,
            TrainingType trainingType,
            YearMonth month,
            boolean includeUndated) {
        var user = accessScopeService.currentUser();
        if (user.role() == UserRole.ADMIN) {
            return trainingListRepository.findAll(includeCompleted, trainingType, month, includeUndated);
        }
        if (user.role() == UserRole.COACH) {
            return trainingListRepository.findForCoach(user.id(), includeCompleted, trainingType, month, includeUndated);
        }
        if (user.role() != UserRole.ATHLETE) {
            throw new AccessDeniedException("Training access is required");
        }
        long athleteId = accessScopeService.currentAthleteId();
        return trainingListRepository.findForAthlete(
                athleteId, includeCompleted, trainingType, month, includeUndated);
    }
}

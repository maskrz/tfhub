package pl.tfhub.api.training.service;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.tfhub.api.common.authorization.AccessScopeService;
import pl.tfhub.api.training.dto.response.TrainingListItemResponse;
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
    public List<TrainingListItemResponse> listTrainings() {
        accessScopeService.requireCoachOrAdmin();
        var user = accessScopeService.currentUser();
        if (user.role() == UserRole.ADMIN) {
            return trainingListRepository.findAllActive();
        }
        return trainingListRepository.findActiveForCoach(user.id());
    }
}

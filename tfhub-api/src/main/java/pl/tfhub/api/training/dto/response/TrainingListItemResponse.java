package pl.tfhub.api.training.dto.response;

import java.time.LocalDate;
import pl.tfhub.api.training.enums.TrainingStatus;
import pl.tfhub.api.traininggroup.dto.response.TrainingGroupResponse;

public record TrainingListItemResponse(
        long id,
        String name,
        LocalDate trainingDate,
        TrainingGroupResponse trainingGroup,
        long definitionCount,
        long participantCount,
        TrainingStatus status) {
}

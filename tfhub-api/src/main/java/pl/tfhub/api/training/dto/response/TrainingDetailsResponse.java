package pl.tfhub.api.training.dto.response;

import java.time.LocalDate;
import java.util.List;
import pl.tfhub.api.traininggroup.dto.response.TrainingGroupResponse;

public record TrainingDetailsResponse(
        long id,
        String name,
        LocalDate trainingDate,
        boolean active,
        TrainingGroupResponse trainingGroup,
        List<TrainingDefinitionDetailsResponse> definitions) {
}

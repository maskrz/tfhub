package pl.tfhub.api.training.dto.response;

import java.util.List;

public record TrainingParticipationDetailsResponse(
        long id,
        long athleteId,
        String firstName,
        String lastName,
        List<TrainingExecutionDetailsResponse> executions) {
}

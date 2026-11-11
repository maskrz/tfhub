package pl.tfhub.api.training.dto.response;

import java.util.List;

public record TrainingDefinitionDetailsResponse(
        long id,
        String name,
        int sequenceNumber,
        List<TrainingSegmentDetailsResponse> segments,
        List<TrainingParticipationDetailsResponse> participations) {
}

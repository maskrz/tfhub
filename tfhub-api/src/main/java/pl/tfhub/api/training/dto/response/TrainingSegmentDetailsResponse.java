package pl.tfhub.api.training.dto.response;

public record TrainingSegmentDetailsResponse(
        long id,
        int sequenceNumber,
        int distanceM,
        Integer restAfterSeconds) {
}

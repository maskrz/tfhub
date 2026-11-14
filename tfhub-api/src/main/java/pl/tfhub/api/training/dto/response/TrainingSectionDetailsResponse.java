package pl.tfhub.api.training.dto.response;

public record TrainingSectionDetailsResponse(
        long id,
        String title,
        String content,
        int sequenceNumber) {
}

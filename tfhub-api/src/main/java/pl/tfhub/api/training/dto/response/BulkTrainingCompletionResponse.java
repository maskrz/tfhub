package pl.tfhub.api.training.dto.response;

import java.util.List;

public record BulkTrainingCompletionResponse(
        List<Long> completedIds,
        List<Long> alreadyCompletedIds,
        List<Long> skippedUndatedIds) {
}

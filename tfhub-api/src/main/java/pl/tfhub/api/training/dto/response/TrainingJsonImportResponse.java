package pl.tfhub.api.training.dto.response;

import java.util.List;

public record TrainingJsonImportResponse(List<Long> createdTrainingIds, int createdCount) {
    public TrainingJsonImportResponse(List<Long> createdTrainingIds) {
        this(createdTrainingIds, createdTrainingIds.size());
    }
}

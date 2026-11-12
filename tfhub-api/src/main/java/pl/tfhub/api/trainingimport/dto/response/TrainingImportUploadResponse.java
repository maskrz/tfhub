package pl.tfhub.api.trainingimport.dto.response;

import pl.tfhub.api.trainingimport.enums.TrainingImportStatus;

public record TrainingImportUploadResponse(
		long id,
		long trainingGroupId,
		TrainingImportStatus status,
		boolean possibleDuplicate) {
}

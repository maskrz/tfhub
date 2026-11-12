package pl.tfhub.api.trainingimport.dto.response;

import pl.tfhub.api.trainingimport.enums.TrainingImportStatus;

public record TrainingImportAcceptanceResponse(
		long importId,
		TrainingImportStatus status,
		long trainingId) {
}

package pl.tfhub.api.trainingimport.dto.response;

import pl.tfhub.api.trainingimport.enums.TrainingImportStatus;

public record TrainingImportRejectionResponse(
		long importId,
		TrainingImportStatus status) {
}

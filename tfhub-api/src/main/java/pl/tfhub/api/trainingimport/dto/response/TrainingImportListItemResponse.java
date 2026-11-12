package pl.tfhub.api.trainingimport.dto.response;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import pl.tfhub.api.trainingimport.enums.TrainingImportStatus;

public record TrainingImportListItemResponse(
		long id,
		long trainingGroupId,
		String trainingGroupName,
		TrainingImportStatus status,
		OffsetDateTime nextRetryAt,
		OffsetDateTime createdAt,
		boolean possibleDuplicate,
		String name,
		LocalDate trainingDate,
		String failureMessage) {
}

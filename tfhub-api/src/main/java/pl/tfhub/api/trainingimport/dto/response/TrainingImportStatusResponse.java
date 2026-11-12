package pl.tfhub.api.trainingimport.dto.response;

import java.time.OffsetDateTime;
import pl.tfhub.api.trainingimport.enums.TrainingImportAttemptStatus;
import pl.tfhub.api.trainingimport.enums.TrainingImportStatus;

public record TrainingImportStatusResponse(
		long id,
		long trainingGroupId,
		TrainingImportStatus status,
		OffsetDateTime createdAt,
		boolean possibleDuplicate,
		Attempt latestAttempt) {

	public record Attempt(
			int attemptNumber,
			TrainingImportAttemptStatus status,
			OffsetDateTime startedAt,
			OffsetDateTime finishedAt,
			String failureCode,
			String failureMessage) {
	}
}

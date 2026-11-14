package pl.tfhub.api.trainingimport.dto.response;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import pl.tfhub.api.trainingimport.enums.TrainingImportStatus;
import pl.tfhub.api.training.enums.TrainingType;

public record TrainingImportListItemResponse(
		long id,
		long trainingGroupId,
		String trainingGroupName,
		TrainingType trainingType,
		TrainingImportStatus status,
		OffsetDateTime nextRetryAt,
		OffsetDateTime createdAt,
		boolean possibleDuplicate,
		String name,
		LocalDate trainingDate,
		String failureMessage) {

	public TrainingImportListItemResponse(
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
		this(id, trainingGroupId, trainingGroupName, TrainingType.TEMPO, status,
				nextRetryAt, createdAt, possibleDuplicate, name, trainingDate, failureMessage);
	}
}

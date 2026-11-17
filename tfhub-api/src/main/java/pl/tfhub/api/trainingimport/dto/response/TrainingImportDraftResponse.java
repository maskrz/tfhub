package pl.tfhub.api.trainingimport.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import pl.tfhub.api.training.enums.TrainingExecutionStatus;
import pl.tfhub.api.training.enums.TrainingType;
import pl.tfhub.api.traininggroup.dto.response.TrainingGroupResponse;
import pl.tfhub.api.trainingimport.enums.TrainingImportAthleteMatchStatus;
import pl.tfhub.api.trainingimport.enums.TrainingImportStatus;

public record TrainingImportDraftResponse(
		long importId,
		TrainingImportStatus status,
		TrainingType trainingType,
		TrainingGroupResponse trainingGroup,
		String name,
		LocalDate trainingDate,
		Map<String, Object> recognitionMetadata,
		boolean recognitionReviewed,
		List<Definition> definitions,
		List<Section> sections,
		Validation validation,
		@JsonInclude(JsonInclude.Include.NON_NULL) String equipment,
		@JsonInclude(JsonInclude.Include.NON_NULL) Map<String, Object> equipmentRecognitionMetadata,
		@JsonInclude(JsonInclude.Include.NON_NULL) Boolean equipmentRecognitionReviewed) {

	public TrainingImportDraftResponse(
			long importId,
			TrainingImportStatus status,
			TrainingGroupResponse trainingGroup,
			String name,
			LocalDate trainingDate,
			Map<String, Object> recognitionMetadata,
			boolean recognitionReviewed,
			List<Definition> definitions,
			Validation validation) {
		this(importId, status, TrainingType.TEMPO, trainingGroup, name, trainingDate,
				recognitionMetadata, recognitionReviewed, definitions, List.of(), validation, null, null, null);
	}

	public record Definition(
			long id,
			String name,
			int sequenceNumber,
			List<Segment> segments,
			List<AthleteRow> athleteRows) {
	}

	public record Section(
			long id,
			int sequenceNumber,
			String title,
			String content,
			Map<String, Object> recognitionMetadata,
			boolean recognitionReviewed) {
	}

	public record Segment(
			long id,
			int sequenceNumber,
			Integer distanceM,
			Integer restAfterSeconds,
			Map<String, Object> recognitionMetadata,
			boolean recognitionReviewed) {
	}

	public record AthleteRow(
			long id,
			String recognizedName,
			TrainingImportAthleteMatchStatus matchStatus,
			TrainingImportAthleteCandidateResponse athlete,
			Map<String, Object> recognitionMetadata,
			List<Execution> executions) {
	}

	public record Execution(
			long id,
			long segmentId,
			int segmentSequenceNumber,
			TrainingExecutionStatus status,
			BigDecimal measuredResult,
			Boolean usesSpikes,
			String recognizedText,
			Map<String, Object> recognitionMetadata,
			boolean recognitionReviewed) {
	}

	public record Validation(boolean readyForAcceptance, List<ValidationIssue> issues) {
	}

	public record ValidationIssue(String code, String path, String message) {
	}
}

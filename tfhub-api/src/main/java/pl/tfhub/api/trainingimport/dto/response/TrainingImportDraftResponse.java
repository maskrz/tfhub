package pl.tfhub.api.trainingimport.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import pl.tfhub.api.training.enums.TrainingExecutionStatus;
import pl.tfhub.api.traininggroup.dto.response.TrainingGroupResponse;
import pl.tfhub.api.trainingimport.enums.TrainingImportAthleteMatchStatus;
import pl.tfhub.api.trainingimport.enums.TrainingImportStatus;

public record TrainingImportDraftResponse(
		long importId,
		TrainingImportStatus status,
		TrainingGroupResponse trainingGroup,
		String name,
		LocalDate trainingDate,
		Map<String, Object> recognitionMetadata,
		boolean recognitionReviewed,
		List<Definition> definitions,
		Validation validation) {

	public record Definition(
			long id,
			String name,
			int sequenceNumber,
			List<Segment> segments,
			List<AthleteRow> athleteRows) {
	}

	public record Segment(
			long id,
			int sequenceNumber,
			int distanceM,
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

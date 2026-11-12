package pl.tfhub.api.trainingimport.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import pl.tfhub.api.training.enums.TrainingExecutionStatus;
import pl.tfhub.api.trainingimport.enums.TrainingImportAthleteMatchStatus;

record TrainingImportRecognizedDraft(
		String name,
		LocalDate trainingDate,
		Map<String, Object> recognitionMetadata,
		List<Definition> definitions) {

	record Definition(
			String name,
			int sequenceNumber,
			List<Segment> segments,
			List<AthleteRow> athleteRows) {
	}

	record Segment(
			int sequenceNumber,
			int distanceM,
			Integer restAfterSeconds,
			Map<String, Object> recognitionMetadata) {
	}

	record AthleteRow(
			String recognizedName,
			Long athleteId,
			TrainingImportAthleteMatchStatus matchStatus,
			Map<String, Object> recognitionMetadata,
			List<Execution> executions) {
	}

	record Execution(
			int segmentSequenceNumber,
			TrainingExecutionStatus status,
			BigDecimal measuredResult,
			Boolean usesSpikes,
			String recognizedText,
			Map<String, Object> recognitionMetadata) {
	}
}

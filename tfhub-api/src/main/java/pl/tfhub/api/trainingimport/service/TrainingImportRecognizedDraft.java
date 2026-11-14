package pl.tfhub.api.trainingimport.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import pl.tfhub.api.training.enums.TrainingExecutionStatus;
import pl.tfhub.api.training.enums.TrainingType;
import pl.tfhub.api.trainingimport.enums.TrainingImportAthleteMatchStatus;

record TrainingImportRecognizedDraft(
		TrainingType trainingType,
		String name,
		LocalDate trainingDate,
		Map<String, Object> recognitionMetadata,
		List<Definition> definitions,
		List<Section> sections) {

	record Section(
			String title,
			String content,
			int sequenceNumber,
			Map<String, Object> recognitionMetadata) {
	}

	record Definition(
			String name,
			int sequenceNumber,
			List<Segment> segments,
			List<AthleteRow> athleteRows) {
	}

	record Segment(
			int sequenceNumber,
			Integer distanceM,
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

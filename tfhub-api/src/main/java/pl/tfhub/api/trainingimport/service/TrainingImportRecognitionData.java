package pl.tfhub.api.trainingimport.service;

import java.util.List;

public record TrainingImportRecognitionData(
		RecognizedField name,
		RecognizedField trainingDate,
		String recognizedGroupName,
		List<Definition> definitions) {

	public record RecognizedField(
			String value,
			String originalText,
			Boolean uncertain,
			String uncertaintyReason) {
	}

	public record Definition(
			Integer sequenceNumber,
			RecognizedField name,
			List<Segment> segments,
			List<AthleteRow> athleteRows) {
	}

	public record Segment(
			Integer sequenceNumber,
			RecognizedField distanceM,
			RecognizedField restAfterSeconds) {
	}

	public record AthleteRow(
			String recognizedName,
			String firstName,
			String lastName,
			Boolean uncertain,
			String uncertaintyReason,
			List<Execution> executions) {
	}

	public record Execution(
			Integer segmentSequenceNumber,
			String recognizedText,
			String status,
			String measuredResult,
			Boolean usesSpikes,
			Boolean uncertain,
			String uncertaintyReason) {
	}
}

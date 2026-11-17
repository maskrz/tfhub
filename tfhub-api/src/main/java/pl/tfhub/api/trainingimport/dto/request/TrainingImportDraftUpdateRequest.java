package pl.tfhub.api.trainingimport.dto.request;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import pl.tfhub.api.training.enums.TrainingExecutionStatus;
import pl.tfhub.api.trainingimport.enums.TrainingImportAthleteMatchStatus;
import tools.jackson.databind.JsonNode;

public record TrainingImportDraftUpdateRequest(
		@NotBlank @Size(max = 255) String name,
		LocalDate trainingDate,
		boolean recognitionReviewed,
		@NotNull List<@Valid Definition> definitions,
		List<@Valid Section> sections,
		JsonNode equipment,
		Boolean equipmentRecognitionReviewed) {

	public TrainingImportDraftUpdateRequest(String name, LocalDate trainingDate, boolean recognitionReviewed,
			List<Definition> definitions, List<Section> sections) {
		this(name, trainingDate, recognitionReviewed, definitions, sections, null, null);
	}

	public TrainingImportDraftUpdateRequest(
			String name,
			LocalDate trainingDate,
			boolean recognitionReviewed,
			List<Definition> definitions) {
		this(name, trainingDate, recognitionReviewed, definitions, List.of(), null, null);
	}

	public record Section(
			Long id,
			@Positive int sequenceNumber,
			@NotBlank @Size(max = 255) String title,
			@NotBlank String content,
			boolean recognitionReviewed) {
	}

	public record Definition(
			Long id,
			@NotBlank @Size(max = 255) String name,
			@Positive int sequenceNumber,
			@NotEmpty List<@Valid Segment> segments,
			@NotNull List<@Valid AthleteRow> athleteRows) {
	}

	public record Segment(
			Long id,
			@Positive int sequenceNumber,
			@Positive Integer distanceM,
			@PositiveOrZero Integer restAfterSeconds,
			boolean recognitionReviewed) {
	}

	public record AthleteRow(
			Long id,
			String recognizedName,
			Long athleteId,
			@NotNull TrainingImportAthleteMatchStatus matchStatus,
			@NotNull List<@Valid Execution> executions) {
	}

	public record Execution(
			Long id,
			@Positive int segmentSequenceNumber,
			@NotNull TrainingExecutionStatus status,
			@DecimalMin("0.0") @Digits(integer = 9, fraction = 3) BigDecimal measuredResult,
			Boolean usesSpikes,
			boolean recognitionReviewed) {
	}
}

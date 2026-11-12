package pl.tfhub.api.trainingimport.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import pl.tfhub.api.athlete.entity.Athlete;
import pl.tfhub.api.athlete.repository.AthleteRepository;
import pl.tfhub.api.training.enums.TrainingExecutionStatus;
import pl.tfhub.api.trainingimport.enums.TrainingImportAthleteMatchStatus;

@Component
public class TrainingImportRecognitionMapper {

	private final ObjectMapper objectMapper;
	private final AthleteRepository athleteRepository;

	public TrainingImportRecognitionMapper(
			ObjectMapper objectMapper,
			AthleteRepository athleteRepository) {
		this.objectMapper = objectMapper;
		this.athleteRepository = athleteRepository;
	}

	public TrainingImportRecognizedDraft validateAndMap(String candidateText, long trainingGroupId) {
		TrainingImportRecognitionData recognized = parse(candidateText);
		validateHeader(recognized);
		List<Athlete> groupAthletes =
				athleteRepository.findByTrainingGroup_IdOrderByLastNameAscFirstNameAscIdAsc(trainingGroupId);
		Set<Long> matchedAthleteIds = new HashSet<>();
		List<TrainingImportRecognizedDraft.Definition> definitions = new ArrayList<>();
		Set<Integer> definitionSequences = new HashSet<>();
		List<Map<String, Object>> definitionRecognition = new ArrayList<>();
		boolean uncertain = isUncertain(recognized.name()) || isUncertain(recognized.trainingDate());

		for (TrainingImportRecognitionData.Definition definition : recognized.definitions()) {
			validateDefinition(definition, definitionSequences);
			uncertain |= isUncertain(definition.name());
			definitionRecognition.add(Map.of(
					"sequenceNumber", definition.sequenceNumber(),
					"name", fieldMetadata(definition.name())));

			Set<Integer> segmentSequences = new HashSet<>();
			List<TrainingImportRecognizedDraft.Segment> segments = new ArrayList<>();
			for (TrainingImportRecognitionData.Segment segment : definition.segments()) {
				validateSegment(segment, segmentSequences);
				int distanceM = parsePositiveInteger(segment.distanceM().value(), "segment distance");
				Integer restAfterSeconds = parseOptionalNonNegativeInteger(
						segment.restAfterSeconds().value(), "segment rest");
				Map<String, Object> metadata = new LinkedHashMap<>();
				boolean segmentUncertain = isUncertain(segment.distanceM())
						|| isUncertain(segment.restAfterSeconds());
				metadata.put("uncertain", segmentUncertain);
				metadata.put("manuallyReviewed", false);
				Map<String, Object> fields = new LinkedHashMap<>();
				fields.put("distanceM", fieldMetadata(segment.distanceM()));
				fields.put("restAfterSeconds", fieldMetadata(segment.restAfterSeconds()));
				metadata.put("fields", fields);
				segments.add(new TrainingImportRecognizedDraft.Segment(
						segment.sequenceNumber(), distanceM, restAfterSeconds, Map.copyOf(metadata)));
			}

			List<TrainingImportRecognizedDraft.AthleteRow> athleteRows = new ArrayList<>();
			Set<Integer> executionSequences;
			for (TrainingImportRecognitionData.AthleteRow row : definition.athleteRows()) {
				validateAthleteRow(row);
				MatchedAthlete matched = matchAthlete(row, groupAthletes, matchedAthleteIds);
				Map<String, Object> athleteMetadata = new LinkedHashMap<>();
				athleteMetadata.put("uncertain", Boolean.TRUE.equals(row.uncertain()) || matched.reason() != null);
				athleteMetadata.put("manuallyReviewed", false);
				athleteMetadata.put("originalText", row.recognizedName());
				putIfPresent(athleteMetadata, "recognizedFirstName", row.firstName());
				putIfPresent(athleteMetadata, "recognizedLastName", row.lastName());
				putIfPresent(athleteMetadata, "uncertaintyReason", row.uncertaintyReason());
				putIfPresent(athleteMetadata, "matchingReason", matched.reason());

				executionSequences = new HashSet<>();
				List<TrainingImportRecognizedDraft.Execution> executions = new ArrayList<>();
				for (TrainingImportRecognitionData.Execution execution : row.executions()) {
					validateExecution(execution, segmentSequences, executionSequences);
					TrainingExecutionStatus status = parseStatus(execution.status());
					BigDecimal result = normalizeResult(execution.measuredResult(), status);
					Map<String, Object> executionMetadata = new LinkedHashMap<>();
					executionMetadata.put("uncertain", Boolean.TRUE.equals(execution.uncertain()));
					executionMetadata.put("manuallyReviewed", false);
					putIfPresent(executionMetadata, "uncertaintyReason", execution.uncertaintyReason());
					putIfPresent(executionMetadata, "recognizedSpikeUsage", execution.usesSpikes());
					executions.add(new TrainingImportRecognizedDraft.Execution(
							execution.segmentSequenceNumber(),
							status,
							result,
							execution.usesSpikes(),
							execution.recognizedText(),
							Map.copyOf(executionMetadata)));
				}
				if (!executionSequences.equals(segmentSequences)) {
					throw invalid("Every athlete row must have exactly one execution for each segment");
				}
				athleteRows.add(new TrainingImportRecognizedDraft.AthleteRow(
						row.recognizedName(),
						matched.athleteId(),
						matched.status(),
						Map.copyOf(athleteMetadata),
						List.copyOf(executions)));
			}

			definitions.add(new TrainingImportRecognizedDraft.Definition(
					definition.name().value().strip(),
					definition.sequenceNumber(),
					List.copyOf(segments),
					List.copyOf(athleteRows)));
		}

		Map<String, Object> rootMetadata = new LinkedHashMap<>();
		rootMetadata.put("uncertain", uncertain);
		rootMetadata.put("manuallyReviewed", false);
		Map<String, Object> rootFields = new LinkedHashMap<>();
		rootFields.put("name", fieldMetadata(recognized.name()));
		rootFields.put("trainingDate", fieldMetadata(recognized.trainingDate()));
		rootMetadata.put("fields", rootFields);
		rootMetadata.put("definitions", List.copyOf(definitionRecognition));
		putIfPresent(rootMetadata, "recognizedGroupName", recognized.recognizedGroupName());

		return new TrainingImportRecognizedDraft(
				recognized.name().value().strip(),
				LocalDate.parse(recognized.trainingDate().value()),
				Map.copyOf(rootMetadata),
				List.copyOf(definitions));
	}

	private TrainingImportRecognitionData parse(String candidateText) {
		if (candidateText == null || candidateText.isBlank()) {
			throw invalid("The model response did not contain structured training data");
		}
		try {
			return objectMapper.readValue(candidateText, TrainingImportRecognitionData.class);
		} catch (JacksonException | IllegalArgumentException exception) {
			throw invalid("The model response did not match the expected training data structure");
		}
	}

	private void validateHeader(TrainingImportRecognitionData recognized) {
		if (recognized == null || recognized.name() == null || recognized.trainingDate() == null
				|| recognized.definitions() == null || recognized.definitions().isEmpty()) {
			throw invalid("The model response is missing required training information");
		}
		requireField(recognized.name(), "training name");
		requireField(recognized.trainingDate(), "training date");
		if (recognized.name().value().isBlank() || recognized.name().value().length() > 255) {
			throw invalid("The recognized training name is invalid");
		}
		try {
			LocalDate.parse(recognized.trainingDate().value());
		} catch (RuntimeException exception) {
			throw invalid("The recognized training date is invalid");
		}
	}

	private void validateDefinition(
			TrainingImportRecognitionData.Definition definition,
			Set<Integer> sequences) {
		if (definition == null || definition.sequenceNumber() == null || definition.sequenceNumber() <= 0
				|| !sequences.add(definition.sequenceNumber()) || definition.name() == null
				|| definition.name().value() == null || definition.name().value().isBlank()
				|| definition.name().value().length() > 255 || definition.name().uncertain() == null
				|| definition.segments() == null || definition.segments().isEmpty()
				|| definition.athleteRows() == null) {
			throw invalid("A recognized training definition is invalid");
		}
	}

	private void validateSegment(TrainingImportRecognitionData.Segment segment, Set<Integer> sequences) {
		if (segment == null || segment.sequenceNumber() == null || segment.sequenceNumber() <= 0
				|| !sequences.add(segment.sequenceNumber())) {
			throw invalid("Training segment ordering is invalid");
		}
		requireField(segment.distanceM(), "segment distance");
		requireField(segment.restAfterSeconds(), "segment rest");
		if (segment.distanceM().value() == null) {
			throw invalid("A training segment distance could not be recognized");
		}
	}

	private void validateAthleteRow(TrainingImportRecognitionData.AthleteRow row) {
		if (row == null || row.recognizedName() == null || row.uncertain() == null
				|| row.executions() == null) {
			throw invalid("A recognized athlete row is invalid");
		}
	}

	private void validateExecution(
			TrainingImportRecognitionData.Execution execution,
			Set<Integer> segmentSequences,
			Set<Integer> executionSequences) {
		if (execution == null || execution.segmentSequenceNumber() == null
				|| !segmentSequences.contains(execution.segmentSequenceNumber())
				|| !executionSequences.add(execution.segmentSequenceNumber())
				|| execution.status() == null || execution.uncertain() == null) {
			throw invalid("Recognized execution data contains an invalid segment reference");
		}
	}

	private MatchedAthlete matchAthlete(
			TrainingImportRecognitionData.AthleteRow row,
			List<Athlete> candidates,
			Set<Long> alreadyMatched) {
		if (Boolean.TRUE.equals(row.uncertain()) || row.firstName() == null || row.lastName() == null
				|| row.firstName().isBlank() || row.lastName().isBlank()) {
			return MatchedAthlete.unresolved("Athlete name is incomplete or uncertain");
		}
		List<Athlete> matches = candidates.stream()
				.filter(candidate -> normalize(candidate.getFirstName()).equals(normalize(row.firstName()))
						&& normalize(candidate.getLastName()).equals(normalize(row.lastName())))
				.toList();
		if (matches.size() != 1) {
			return MatchedAthlete.unresolved(
					matches.isEmpty() ? "No athlete matched the recognized name" : "The recognized name is ambiguous");
		}
		Athlete match = matches.getFirst();
		if (!alreadyMatched.add(match.getId())) {
			return MatchedAthlete.unresolved("The athlete was recognized in more than one row");
		}
		return new MatchedAthlete(match.getId(), TrainingImportAthleteMatchStatus.MATCHED, null);
	}

	private String normalize(String value) {
		return Normalizer.normalize(value, Normalizer.Form.NFKC)
				.strip()
				.replaceAll("\\s+", " ")
				.toLowerCase(Locale.ROOT);
	}

	private TrainingExecutionStatus parseStatus(String value) {
		try {
			return TrainingExecutionStatus.valueOf(value);
		} catch (IllegalArgumentException | NullPointerException exception) {
			throw invalid("A recognized execution status is invalid");
		}
	}

	private BigDecimal normalizeResult(String value, TrainingExecutionStatus status) {
		if (status != TrainingExecutionStatus.RECORDED) {
			if (value != null) {
				throw invalid("Only RECORDED executions may contain a measured result");
			}
			return null;
		}
		if (value == null || value.isBlank()) {
			throw invalid("A RECORDED execution must contain a measured result");
		}
		String normalizedText = value.strip().replace(',', '.')
				.replaceFirst("(?i)\\s*K\\s*$", "");
		BigDecimal result;
		try {
			int separator = normalizedText.indexOf(':');
			if (separator >= 0) {
				if (separator != normalizedText.lastIndexOf(':')) {
					throw invalid("The recognized running time is invalid");
				}
				long minutes = Long.parseLong(normalizedText.substring(0, separator).strip());
				BigDecimal seconds = new BigDecimal(normalizedText.substring(separator + 1).strip());
				if (minutes < 0 || seconds.signum() < 0 || seconds.compareTo(BigDecimal.valueOf(60)) >= 0) {
					throw invalid("The recognized running time is outside the valid range");
				}
				result = BigDecimal.valueOf(minutes).multiply(BigDecimal.valueOf(60)).add(seconds);
			} else {
				result = new BigDecimal(normalizedText);
			}
		} catch (NumberFormatException exception) {
			throw invalid("The recognized running time is invalid");
		}
		if (result.signum() < 0 || result.precision() - result.scale() > 9 || result.scale() > 3) {
			throw invalid("The recognized running time is outside the supported numeric range");
		}
		if (result.scale() < 2) {
			result = result.setScale(2, RoundingMode.UNNECESSARY);
		}
		return result;
	}

	private int parsePositiveInteger(String value, String fieldName) {
		int parsed = parseInteger(value, fieldName);
		if (parsed <= 0) {
			throw invalid("The recognized " + fieldName + " must be positive");
		}
		return parsed;
	}

	private Integer parseOptionalNonNegativeInteger(String value, String fieldName) {
		if (value == null) {
			return null;
		}
		int parsed = parseInteger(value, fieldName);
		if (parsed < 0) {
			throw invalid("The recognized " + fieldName + " cannot be negative");
		}
		return parsed;
	}

	private int parseInteger(String value, String fieldName) {
		try {
			return Integer.parseInt(value.strip());
		} catch (NumberFormatException | NullPointerException exception) {
			throw invalid("The recognized " + fieldName + " is invalid");
		}
	}

	private void requireField(TrainingImportRecognitionData.RecognizedField field, String fieldName) {
		if (field == null || field.uncertain() == null) {
			throw invalid("Recognition metadata is missing for " + fieldName);
		}
	}

	private boolean isUncertain(TrainingImportRecognitionData.RecognizedField field) {
		return Boolean.TRUE.equals(field.uncertain());
	}

	private Map<String, Object> fieldMetadata(TrainingImportRecognitionData.RecognizedField field) {
		Map<String, Object> metadata = new LinkedHashMap<>();
		metadata.put("uncertain", Boolean.TRUE.equals(field.uncertain()));
		metadata.put("manuallyReviewed", false);
		putIfPresent(metadata, "originalText", field.originalText());
		putIfPresent(metadata, "uncertaintyReason", field.uncertaintyReason());
		return Map.copyOf(metadata);
	}

	private void putIfPresent(Map<String, Object> target, String key, Object value) {
		if (value != null) {
			target.put(key, value);
		}
	}

	private TrainingImportProcessingException invalid(String safeMessage) {
		return new TrainingImportProcessingException("OUTPUT_VALIDATION_FAILED", safeMessage);
	}

	private record MatchedAthlete(
			Long athleteId,
			TrainingImportAthleteMatchStatus status,
			String reason) {

		static MatchedAthlete unresolved(String reason) {
			return new MatchedAthlete(null, TrainingImportAthleteMatchStatus.UNRESOLVED, reason);
		}
	}
}

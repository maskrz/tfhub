package pl.tfhub.api.trainingimport.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;
import pl.tfhub.api.athlete.service.AthleteService;
import pl.tfhub.api.athlete.service.AthleteService.ImportCandidate;
import pl.tfhub.api.training.enums.TrainingExecutionStatus;
import pl.tfhub.api.trainingimport.enums.TrainingImportAthleteMatchStatus;

@Component
public class TrainingImportRecognitionMapper {

	private static final DateTimeFormatter DEFAULT_NAME_DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.uuuu");

	private final ObjectMapper objectMapper;
	private final AthleteService athleteService;
	private final TrainingImportAthleteMatcher athleteMatcher;

	public TrainingImportRecognitionMapper(
			ObjectMapper objectMapper,
			AthleteService athleteService,
			TrainingImportAthleteMatcher athleteMatcher) {
		this.objectMapper = objectMapper;
		this.athleteService = athleteService;
		this.athleteMatcher = athleteMatcher;
	}

	public TrainingImportRecognizedDraft validateAndMap(String candidateText, long trainingGroupId) {
		TrainingImportRecognitionData recognized = parse(candidateText);
		validateHeader(recognized);
		LocalDate trainingDate = LocalDate.parse(recognized.trainingDate().value());
		List<ImportCandidate> groupAthletes = athleteService.findImportCandidates(trainingGroupId);
		Map<TrainingImportRecognitionData.AthleteRow, TrainingImportAthleteMatcher.Match> matches =
				new IdentityHashMap<>();
		Map<Long, Integer> claims = new HashMap<>();
		Set<Integer> validatedSequences = new HashSet<>();
		for (TrainingImportRecognitionData.Definition definition : recognized.definitions()) {
			validateDefinition(definition, validatedSequences);
			for (TrainingImportRecognitionData.AthleteRow row : definition.athleteRows()) {
				validateAthleteRow(row);
				TrainingImportAthleteMatcher.Match match = athleteMatcher.match(row, groupAthletes);
				matches.put(row, match);
				if (match.athleteId() != null) {
					claims.merge(match.athleteId(), 1, Integer::sum);
				}
			}
		}
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
			}
			int lastSequence = segmentSequences.stream().mapToInt(Integer::intValue).max().orElseThrow();
			for (TrainingImportRecognitionData.Segment segment : definition.segments()) {
				Integer distanceM = parseRecognizedDistance(segment.distanceM());
				Integer restAfterSeconds = parseRecognizedRest(segment.restAfterSeconds());
				boolean defaultFinalRest = segment.sequenceNumber() == lastSequence
						&& restAfterSeconds == null && isMissingRest(segment.restAfterSeconds());
				if (defaultFinalRest) {
					restAfterSeconds = 0;
				}
				Map<String, Object> metadata = new LinkedHashMap<>();
				boolean segmentUncertain = distanceM == null || distanceUncertain(segment.distanceM())
						|| (!defaultFinalRest
								&& (restAfterSeconds == null || isUncertain(segment.restAfterSeconds())));
				metadata.put("uncertain", segmentUncertain);
				metadata.put("manuallyReviewed", false);
				Map<String, Object> fields = new LinkedHashMap<>();
				fields.put("distanceM", distanceMetadata(segment.distanceM(), distanceM));
				fields.put("restAfterSeconds", defaultFinalRest
						? Map.of("uncertain", false, "manuallyReviewed", false)
						: restMetadata(segment.restAfterSeconds(), restAfterSeconds));
				metadata.put("fields", fields);
				segments.add(new TrainingImportRecognizedDraft.Segment(
						segment.sequenceNumber(), distanceM, restAfterSeconds, Map.copyOf(metadata)));
			}

			List<TrainingImportRecognizedDraft.AthleteRow> athleteRows = new ArrayList<>();
			Set<Integer> executionSequences;
			for (TrainingImportRecognitionData.AthleteRow row : definition.athleteRows()) {
				validateAthleteRow(row);
				TrainingImportAthleteMatcher.Match match = matches.get(row);
				boolean duplicate = match.athleteId() != null && claims.get(match.athleteId()) > 1;
				MatchedAthlete matched = duplicate
						? MatchedAthlete.unresolved("The athlete was recognized in more than one row")
						: match.athleteId() == null ? MatchedAthlete.unresolved(match.reason())
						: new MatchedAthlete(match.athleteId(), TrainingImportAthleteMatchStatus.MATCHED, null);
				Map<String, Object> athleteMetadata = new LinkedHashMap<>();
				athleteMetadata.put("uncertain", Boolean.TRUE.equals(row.uncertain()) || matched.reason() != null);
				athleteMetadata.put("manuallyReviewed", false);
				athleteMetadata.put("originalText", row.recognizedName());
				putIfPresent(athleteMetadata, "recognizedFirstName", row.firstName());
				putIfPresent(athleteMetadata, "recognizedLastName", row.lastName());
				putIfPresent(athleteMetadata, "uncertaintyReason", row.uncertaintyReason());
				putIfPresent(athleteMetadata, "matchingReason", matched.reason());
				if (!match.suggestionIds().isEmpty()) {
					athleteMetadata.put("suggestedAthleteIds", match.suggestionIds());
				}

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
					nameOrDefault(definition.name(), "Cała grupa"),
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
				nameOrDefault(recognized.name(), "Trening " + trainingDate.format(DEFAULT_NAME_DATE_FORMAT)),
				trainingDate,
				Map.copyOf(rootMetadata),
				List.copyOf(definitions));
	}

	private TrainingImportRecognitionData parse(String candidateText) {
		if (candidateText == null || candidateText.isBlank()) {
			throw invalid("The model response did not contain structured training data");
		}
		try {
			JsonNode root = objectMapper.readTree(candidateText);
			normalizeSegmentFields(root);
			return objectMapper.treeToValue(root, TrainingImportRecognitionData.class);
		} catch (JacksonException | IllegalArgumentException exception) {
			throw invalid("The model response did not match the expected training data structure");
		}
	}

	private void normalizeSegmentFields(JsonNode root) {
		JsonNode definitions = root.path("definitions");
		if (!definitions.isArray()) {
			return;
		}
		for (JsonNode definition : definitions) {
			JsonNode segments = definition.path("segments");
			if (!segments.isArray()) {
				continue;
			}
			for (JsonNode segment : segments) {
				if (!(segment instanceof ObjectNode segmentObject)) {
					continue;
				}
				normalizeSegmentField(segmentObject, "distanceM");
				normalizeSegmentField(segmentObject, "restAfterSeconds");
			}
		}
	}

	private void normalizeSegmentField(ObjectNode segmentObject, String fieldName) {
		JsonNode field = segmentObject.get(fieldName);
		if (field != null && !field.isNull() && !(field instanceof ObjectNode)) {
			String original = field.isTextual() ? field.textValue() : field.toString();
			ObjectNode replacement = segmentObject.putObject(fieldName);
			replacement.putNull("value");
			replacement.put("originalText", original);
			replacement.put("uncertain", true);
		} else if (field instanceof ObjectNode fieldObject) {
			JsonNode value = field.get("value");
			if (value != null && (value.isArray() || value.isObject())) {
				if (!field.hasNonNull("originalText")) {
					fieldObject.put("originalText", value.toString());
				}
				fieldObject.putNull("value");
			} else if (value != null && !value.isNull() && !value.isTextual()) {
				fieldObject.put("value", value.toString());
			}
		}
	}

	private void validateHeader(TrainingImportRecognitionData recognized) {
		if (recognized == null || recognized.trainingDate() == null
				|| recognized.definitions() == null || recognized.definitions().isEmpty()) {
			throw invalid("The model response is missing required training information");
		}
		validateName(recognized.name(), "training name");
		requireField(recognized.trainingDate(), "training date");
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
				|| !sequences.add(definition.sequenceNumber())
				|| definition.segments() == null || definition.segments().isEmpty()
				|| definition.athleteRows() == null) {
			throw invalid("A recognized training definition is invalid");
		}
		validateName(definition.name(), "definition name");
	}

	private void validateName(TrainingImportRecognitionData.RecognizedField field, String fieldName) {
		if (field != null && field.value() != null && !field.value().isBlank()) {
			requireField(field, fieldName);
			if (field.value().length() > 255) {
				throw invalid("The recognized " + fieldName + " is invalid");
			}
		}
	}

	private String nameOrDefault(TrainingImportRecognitionData.RecognizedField field, String fallback) {
		return field == null || field.value() == null || field.value().isBlank()
				? fallback : field.value().strip();
	}

	private void validateSegment(TrainingImportRecognitionData.Segment segment, Set<Integer> sequences) {
		if (segment == null || segment.sequenceNumber() == null || segment.sequenceNumber() <= 0
				|| !sequences.add(segment.sequenceNumber())) {
			throw invalid("Training segment ordering is invalid");
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
		String normalizedText = value.strip()
				.replaceFirst("(?i)\\s*K\\s*$", "")
				.strip();
		BigDecimal result = parseRunningTime(normalizedText);
		if (result.signum() < 0 || result.precision() - result.scale() > 9 || result.scale() > 3) {
			throw invalid("The recognized running time is outside the supported numeric range");
		}
		if (result.scale() < 2) {
			result = result.setScale(2, RoundingMode.UNNECESSARY);
		}
		return result;
	}

	private BigDecimal parseRunningTime(String value) {
		try {
			java.util.regex.Matcher threeGroups = java.util.regex.Pattern
					.compile("^(\\d+)[\\s:;,.]+(\\d{1,2})[\\s:;,.]+(\\d{1,2})$")
					.matcher(value);
			if (threeGroups.matches()) {
				long minutes = Long.parseLong(threeGroups.group(1));
				int seconds = Integer.parseInt(threeGroups.group(2));
				if (seconds >= 60) {
					throw invalid("The recognized running time is outside the valid range");
				}
				BigDecimal fraction = new BigDecimal("0." + threeGroups.group(3));
				return BigDecimal.valueOf(minutes).multiply(BigDecimal.valueOf(60))
						.add(BigDecimal.valueOf(seconds)).add(fraction);
			}
			java.util.regex.Matcher minutesSecondsAndFraction = java.util.regex.Pattern
					.compile("^(\\d+)[:;](\\d{1,2})[,.](\\d{1,2})$")
					.matcher(value);
			if (minutesSecondsAndFraction.matches()) {
				long minutes = Long.parseLong(minutesSecondsAndFraction.group(1));
				int seconds = Integer.parseInt(minutesSecondsAndFraction.group(2));
				if (seconds >= 60) {
					throw invalid("The recognized running time is outside the valid range");
				}
				return BigDecimal.valueOf(minutes).multiply(BigDecimal.valueOf(60))
						.add(new BigDecimal(minutesSecondsAndFraction.group(2)
								+ "." + minutesSecondsAndFraction.group(3)));
			}
			java.util.regex.Matcher minutesAndSeconds = java.util.regex.Pattern
					.compile("^(\\d+)[:;\\s]+(\\d{1,2})$")
					.matcher(value);
			if (minutesAndSeconds.matches()) {
				int seconds = Integer.parseInt(minutesAndSeconds.group(2));
				if (seconds >= 60) {
					throw invalid("The recognized running time is outside the valid range");
				}
				return BigDecimal.valueOf(Long.parseLong(minutesAndSeconds.group(1)))
						.multiply(BigDecimal.valueOf(60))
						.add(BigDecimal.valueOf(seconds));
			}
			if (value.matches("\\d+[,.]\\d{1,2}")) {
				value = value.replace(',', '.');
			}
			return new BigDecimal(value);
		} catch (NumberFormatException exception) {
			throw invalid("The recognized running time is invalid");
		}
	}

	private Integer parseRecognizedDistance(TrainingImportRecognitionData.RecognizedField field) {
		if (field == null || field.value() == null) {
			return null;
		}
		try {
			int value = Integer.parseInt(field.value().strip());
			return value > 0 ? value : null;
		} catch (NumberFormatException exception) {
			return null;
		}
	}

	private Map<String, Object> distanceMetadata(
			TrainingImportRecognitionData.RecognizedField field, Integer distance) {
		Map<String, Object> metadata = new LinkedHashMap<>();
		metadata.put("uncertain", distance == null || distanceUncertain(field));
		metadata.put("manuallyReviewed", false);
		if (field != null) {
			putIfPresent(metadata, "originalText",
					field.originalText() != null ? field.originalText() : field.value());
			putIfPresent(metadata, "uncertaintyReason", field.uncertaintyReason());
		}
		if (distance == null) {
			metadata.putIfAbsent("uncertaintyReason", "Segment distance could not be recognized");
		}
		return Map.copyOf(metadata);
	}

	private boolean distanceUncertain(TrainingImportRecognitionData.RecognizedField field) {
		return field == null || !Boolean.FALSE.equals(field.uncertain());
	}

	private Integer parseRecognizedRest(TrainingImportRecognitionData.RecognizedField field) {
		if (field == null || field.value() == null) {
			return null;
		}
		try {
			int value = Integer.parseInt(field.value().strip());
			return value >= 0 ? value : null;
		} catch (NumberFormatException exception) {
			return null;
		}
	}

	private boolean isMissingRest(TrainingImportRecognitionData.RecognizedField field) {
		return field == null
				|| ((field.value() == null || field.value().isBlank())
						&& (field.originalText() == null || field.originalText().isBlank()));
	}

	private Map<String, Object> restMetadata(
			TrainingImportRecognitionData.RecognizedField field, Integer rest) {
		Map<String, Object> metadata = new LinkedHashMap<>(fieldMetadata(field));
		if (rest == null) {
			metadata.put("uncertain", true);
			if (field != null) {
				putIfPresent(metadata, "originalText",
						field.originalText() != null ? field.originalText() : field.value());
			}
			metadata.putIfAbsent("uncertaintyReason", "Segment rest requires confirmation");
		}
		return Map.copyOf(metadata);
	}

	private void requireField(TrainingImportRecognitionData.RecognizedField field, String fieldName) {
		if (field == null || field.uncertain() == null) {
			throw invalid("Recognition metadata is missing for " + fieldName);
		}
	}

	private boolean isUncertain(TrainingImportRecognitionData.RecognizedField field) {
		return field == null || field.uncertain() == null || Boolean.TRUE.equals(field.uncertain());
	}

	private Map<String, Object> fieldMetadata(TrainingImportRecognitionData.RecognizedField field) {
		Map<String, Object> metadata = new LinkedHashMap<>();
		metadata.put("uncertain", isUncertain(field));
		metadata.put("manuallyReviewed", false);
		if (field != null) {
			putIfPresent(metadata, "originalText", field.originalText());
			putIfPresent(metadata, "uncertaintyReason", field.uncertaintyReason());
		}
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

package pl.tfhub.api.trainingimport.service;

import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import pl.tfhub.api.athlete.entity.Athlete;
import pl.tfhub.api.athlete.repository.AthleteRepository;
import pl.tfhub.api.athlete.service.AthleteService;
import pl.tfhub.api.training.enums.TrainingExecutionStatus;
import pl.tfhub.api.trainingimport.enums.TrainingImportAthleteMatchStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TrainingImportRecognitionMapperTests {

	private TrainingImportRecognitionMapper mapper(AthleteRepository repository) {
		AthleteService service = mock(AthleteService.class);
		when(service.normalizeImportName(org.mockito.ArgumentMatchers.anyString())).thenAnswer(invocation ->
				java.text.Normalizer.normalize(invocation.getArgument(0), java.text.Normalizer.Form.NFC)
						.strip().replaceAll("\\s+", " ").toLowerCase(java.util.Locale.ROOT));
		when(service.findImportCandidates(org.mockito.ArgumentMatchers.anyLong())).thenAnswer(invocation ->
				repository.findByTrainingGroup_IdOrderByLastNameAscFirstNameAscIdAsc(invocation.getArgument(0))
						.stream().map(athlete -> new AthleteService.ImportCandidate(
								athlete.getId(), athlete.getFirstName(), athlete.getLastName(),
								(athlete.getFirstName() + " " + athlete.getLastName()).toLowerCase(java.util.Locale.ROOT),
								List.of())).toList());
		return new TrainingImportRecognitionMapper(new ObjectMapper(), service, new TrainingImportAthleteMatcher(service));
	}

	@Test
	void defaultsOnlyAnAbsentFinalRestToZeroWithoutRestUncertainty() {
		var mapper = mapper(mock(AthleteRepository.class));
		String input = recognition("[]").replace("\"sequenceNumber\":2,\"distanceM\"", "\"sequenceNumber\":5,\"distanceM\"");
		var definition = mapper.validateAndMap(input, 7L).definitions().getFirst();
		var finalSegment = definition.segments().get(1);
		assertEquals(5, finalSegment.sequenceNumber());
		assertEquals(0, finalSegment.restAfterSeconds());
		assertEquals(Boolean.FALSE, finalSegment.recognitionMetadata().get("uncertain"));
		var fields = (java.util.Map<?, ?>) finalSegment.recognitionMetadata().get("fields");
		assertEquals(Boolean.FALSE, ((java.util.Map<?, ?>) fields.get("restAfterSeconds")).get("uncertain"));
		assertNull(definition.segments().get(2).restAfterSeconds());

		String emptyRest = "\"restAfterSeconds\":{\"value\":null,\"originalText\":null,\n"
				+ "        \"uncertain\":false,\"uncertaintyReason\":null}";
		String invalidFinalRest = input.replaceFirst(java.util.regex.Pattern.quote(emptyRest),
				java.util.regex.Matcher.quoteReplacement("\"restAfterSeconds\":{\"value\":\"???\",\"uncertain\":false}"));
		assertNull(mapper.validateAndMap(invalidFinalRest, 7L).definitions().getFirst().segments().get(1).restAfterSeconds());
		String presentFinalRest = input.replaceFirst(java.util.regex.Pattern.quote(emptyRest),
				java.util.regex.Matcher.quoteReplacement("\"restAfterSeconds\":{\"value\":\"90\",\"uncertain\":false}"));
		assertEquals(90, mapper.validateAndMap(presentFinalRest, 7L).definitions().getFirst().segments().get(1).restAfterSeconds());
		var uncertainDistance = mapper.validateAndMap(input.replace("\"value\":\"200\"", "\"value\":null"), 7L)
				.definitions().getFirst().segments().get(1);
		assertEquals(0, uncertainDistance.restAfterSeconds());
		assertEquals(Boolean.TRUE, uncertainDistance.recognitionMetadata().get("uncertain"));
	}

	@Test
	void retainsMissingOrInvalidRestAsUncertainForReview() {
		var mapper = mapper(mock(AthleteRepository.class));
		String input = recognition("[]");
		String rest = "\"restAfterSeconds\":{\"value\":\"60\",\"originalText\":\"60\",\n"
				+ "        \"uncertain\":false,\"uncertaintyReason\":null}";
		for (String replacement : List.of(
				"\"restAfterSeconds\":null", "\"restAfterSeconds\":{}",
				"\"restAfterSeconds\":{\"value\":null,\"uncertain\":false}",
				"\"restAfterSeconds\":{\"value\":\"\",\"uncertain\":false}",
				"\"restAfterSeconds\":{\"value\":\"???\",\"uncertain\":false}",
				"\"restAfterSeconds\":{\"value\":\"-5\",\"uncertain\":false}",
				"\"restAfterSeconds\":{\"value\":{},\"uncertain\":false}",
				"\"restAfterSeconds\":\"???\"")) {
			String response = input.replace(rest, replacement);
			var segment = mapper.validateAndMap(response, 7L).definitions().getFirst().segments().getFirst();
			assertNull(segment.restAfterSeconds(), replacement);
			assertEquals(400, segment.distanceM());
			assertEquals(Boolean.TRUE, segment.recognitionMetadata().get("uncertain"));
			var fields = (java.util.Map<?, ?>) segment.recognitionMetadata().get("fields");
			var restField = (java.util.Map<?, ?>) fields.get("restAfterSeconds");
			assertEquals(Boolean.TRUE, restField.get("uncertain"), replacement);
		}
		var missing = mapper.validateAndMap(input.replace("," + rest, ""), 7L);
		assertNull(missing.definitions().getFirst().segments().getFirst().restAfterSeconds());
		assertEquals(Boolean.TRUE, missing.definitions().getFirst().segments().getFirst()
				.recognitionMetadata().get("uncertain"));

		var valid = mapper.validateAndMap(input.replace("\"value\":\"60\"", "\"value\":\"0\""), 7L);
		assertEquals(0, valid.definitions().getFirst().segments().getFirst().restAfterSeconds());
		var fields = (java.util.Map<?, ?>) valid.definitions().getFirst().segments().getFirst()
				.recognitionMetadata().get("fields");
		assertEquals(Boolean.FALSE, ((java.util.Map<?, ?>) fields.get("restAfterSeconds")).get("uncertain"));
		var missingMetadata = mapper.validateAndMap(input.replace(rest,
				"\"restAfterSeconds\":{\"value\":\"60\"}"), 7L);
		var segment = missingMetadata.definitions().getFirst().segments().getFirst();
		assertEquals(60, segment.restAfterSeconds());
		var missingFields = (java.util.Map<?, ?>) segment.recognitionMetadata().get("fields");
		assertEquals(Boolean.TRUE, ((java.util.Map<?, ?>) missingFields.get("restAfterSeconds")).get("uncertain"));
	}

	@Test
	void suppliesDefaultNamesForBlankNullAndMissingFields() {
		TrainingImportRecognitionMapper mapper =
				mapper(mock(AthleteRepository.class));
		String input = recognition("[]");
		for (String value : List.of("\"\"", "\"   \"", "null")) {
			String response = input.replace("\"value\":\"Recognized session\"", "\"value\":" + value)
					.replace("\"value\":\"Main set\"", "\"value\":" + value);
			var draft = mapper.validateAndMap(response, 7L);
			assertEquals("Trening 10.11.2026", draft.name(), value);
			assertEquals("Cała grupa", draft.definitions().getFirst().name(), value);
			assertEquals(Boolean.TRUE, draft.recognitionMetadata().get("uncertain"));
			assertEquals(400, draft.definitions().getFirst().segments().getFirst().distanceM());
		}
		for (String replacement : List.of("", "\"name\":null,")) {
			String missingNames = input.replaceAll("\"name\":\\{[^}]*\\},", replacement);
			var draft = mapper.validateAndMap(missingNames, 7L);
			assertEquals("Trening 10.11.2026", draft.name());
			assertEquals("Cała grupa", draft.definitions().getFirst().name());
			assertEquals(Boolean.TRUE, draft.recognitionMetadata().get("uncertain"));
			assertEquals(4, draft.definitions().getFirst().segments().size());
		}
	}

	@Test
	void retainsRecognizedNamesAndStillRejectsInvalidDatesAndOversizedNames() {
		TrainingImportRecognitionMapper mapper =
				mapper(mock(AthleteRepository.class));
		String input = recognition("[]");
		var draft = mapper.validateAndMap(input, 7L);
		assertEquals("Recognized session", draft.name());
		assertEquals("Main set", draft.definitions().getFirst().name());
		assertThrows(TrainingImportProcessingException.class,
				() -> mapper.validateAndMap(input.replace("2026-11-10", "invalid-date"), 7L));
		assertThrows(TrainingImportProcessingException.class,
				() -> mapper.validateAndMap(input.replace("Recognized session", "a".repeat(256)), 7L));
		assertThrows(TrainingImportProcessingException.class,
				() -> mapper.validateAndMap(input.replace("Main set", "a".repeat(256)), 7L));
	}

	@Test
	void retainsUnresolvedDistancesAndOtherRecognizedData() {
		TrainingImportRecognitionMapper mapper =
				mapper(mock(AthleteRepository.class));
		String input = recognition("""
				[{"recognizedName":"Runner Name","firstName":"Runner","lastName":"Name",
				  "uncertain":false,"uncertaintyReason":null,"executions":%s}]
				""".formatted(executions()));
		for (String replacement : List.of("null", "\"\"", "\"not a distance\"", "\"0\"", "\"-10\"", "true")) {
			var draft = mapper.validateAndMap(input.replace("\"value\":\"400\"", "\"value\":" + replacement), 7L);
			var definition = draft.definitions().getFirst();
			var segment = definition.segments().getFirst();
			assertNull(segment.distanceM(), replacement);
			assertEquals(1, segment.sequenceNumber());
			assertEquals(60, segment.restAfterSeconds());
			assertEquals(Boolean.TRUE, segment.recognitionMetadata().get("uncertain"));
			assertEquals(200, definition.segments().get(1).distanceM());
			assertEquals(TrainingExecutionStatus.RECORDED, definition.athleteRows().getFirst()
					.executions().getFirst().status());
		}
		var draft = mapper.validateAndMap(input.replaceFirst(
				"\"distanceM\":\\{[^}]*\\}", "\"distanceM\":null"), 7L);
		assertNull(draft.definitions().getFirst().segments().getFirst().distanceM());
		for (String field : List.of("\"distanceM\":\"\"", "\"distanceM\":{\"value\":{}}",
				"\"distanceM\":{\"value\":[]}")) {
			draft = mapper.validateAndMap(input.replaceFirst("\"distanceM\":\\{[^}]*\\}", field), 7L);
			assertNull(draft.definitions().getFirst().segments().getFirst().distanceM(), field);
			assertEquals(Boolean.TRUE, draft.definitions().getFirst().segments().getFirst()
					.recognitionMetadata().get("uncertain"));
		}
		draft = mapper.validateAndMap(input.replace("\"value\":\"400\"", "\"value\":400"), 7L);
		assertEquals(400, draft.definitions().getFirst().segments().getFirst().distanceM());
	}

	@Test
	void normalizesTimesAndPreservesStatusSpikeAndUncertaintyIndependently() {
		AthleteRepository athleteRepository = mock(AthleteRepository.class);
		Athlete ada = mock(Athlete.class);
		when(ada.getId()).thenReturn(41L);
		when(ada.getFirstName()).thenReturn("Ada");
		when(ada.getLastName()).thenReturn("Lovelace");
		when(athleteRepository.findByTrainingGroup_IdOrderByLastNameAscFirstNameAscIdAsc(7L))
				.thenReturn(List.of(ada));
		TrainingImportRecognitionMapper mapper =
				mapper(athleteRepository);

		var draft = mapper.validateAndMap(recognition(
				"""
				[
				  {"recognizedName":"Ada Lovelace","firstName":"Ada","lastName":"Lovelace","uncertain":false,
				   "uncertaintyReason":null,"executions":%s},
				  {"recognizedName":"Ada Lovelace","firstName":"Ada","lastName":"Lovelace","uncertain":false,
				   "uncertaintyReason":null,"executions":%s},
				  {"recognizedName":"Unknown Runner","firstName":"Unknown","lastName":"Runner","uncertain":false,
				   "uncertaintyReason":null,"executions":%s}
				]
				""".formatted(executions(), executions(), executions())), 7L);

		var rows = draft.definitions().getFirst().athleteRows();
		assertEquals(TrainingImportAthleteMatchStatus.UNRESOLVED, rows.get(0).matchStatus());
		assertNull(rows.get(0).athleteId());
		assertEquals(TrainingImportAthleteMatchStatus.UNRESOLVED, rows.get(1).matchStatus());
		assertEquals(TrainingImportAthleteMatchStatus.UNRESOLVED, rows.get(2).matchStatus());
		var executions = rows.getFirst().executions();
		assertEquals(new java.math.BigDecimal("62.30"), executions.get(0).measuredResult());
		assertEquals(TrainingExecutionStatus.RECORDED, executions.get(0).status());
		assertEquals(Boolean.TRUE, executions.get(0).usesSpikes());
		assertEquals(Boolean.TRUE, executions.get(0).recognitionMetadata().get("uncertain"));
		assertEquals(TrainingExecutionStatus.COMPLETED_WITHOUT_RESULT, executions.get(1).status());
		assertEquals(Boolean.FALSE, executions.get(1).usesSpikes());
		assertEquals(TrainingExecutionStatus.NOT_COMPLETED, executions.get(2).status());
		assertEquals(Boolean.TRUE, executions.get(2).usesSpikes());
		assertEquals(TrainingExecutionStatus.PENDING, executions.get(3).status());
		assertEquals(Boolean.TRUE, executions.get(3).usesSpikes());
		assertEquals(Boolean.TRUE, draft.definitions().getFirst().segments().getFirst()
				.recognitionMetadata().get("uncertain"));
	}

	@Test
	void rejectsMissingOrNegativeRecordedResultsAndResultsOnOtherStatuses() {
		TrainingImportRecognitionMapper mapper =
				mapper(mock(AthleteRepository.class));
		for (String execution : List.of(
				execution("RECORDED", "null"),
				execution("RECORDED", "\"-0.1\""),
				execution("PENDING", "\"12.3\""))) {
			String input = recognition("""
					[{"recognizedName":"Ada Lovelace","firstName":"Ada","lastName":"Lovelace",
					  "uncertain":false,"uncertaintyReason":null,"executions":[%s]}]
					""".formatted(execution));
			assertThrows(TrainingImportProcessingException.class,
					() -> mapper.validateAndMap(input, 7L));
		}
	}

	@Test
	void offersApproximateSurnamesOnlyAsManualSuggestions() {
		Athlete ada = athlete(41L, "Ada", "Lovelace");
		Athlete ann = athlete(42L, "Ben", "Lovelace");
		AthleteRepository athleteRepository = mock(AthleteRepository.class);
		when(athleteRepository.findByTrainingGroup_IdOrderByLastNameAscFirstNameAscIdAsc(7L))
				.thenReturn(List.of(ada, ann));
		TrainingImportRecognitionMapper mapper =
				mapper(athleteRepository);

		var draft = mapper.validateAndMap(recognition("""
				[{"recognizedName":"Ada Lovelece","firstName":"Ada","lastName":"Lovelece",
				  "uncertain":false,"uncertaintyReason":null,"executions":%s}]
				""".formatted(executions())), 7L);

		var row = draft.definitions().getFirst().athleteRows().getFirst();
		assertEquals(TrainingImportAthleteMatchStatus.UNRESOLVED, row.matchStatus());
		assertNull(row.athleteId());
		assertEquals(Boolean.TRUE, row.recognitionMetadata().get("uncertain"));
		assertEquals(List.of(41L, 42L), row.recognitionMetadata().get("suggestedAthleteIds"));
	}

	@Test
	void leavesApproximateSurnameMatchesUnresolvedWhenFirstInitialIsAmbiguous() {
		Athlete ada = athlete(41L, "Ada", "Lovelace");
		Athlete alice = athlete(42L, "Alice", "Lovelace");
		AthleteRepository athleteRepository = mock(AthleteRepository.class);
		when(athleteRepository.findByTrainingGroup_IdOrderByLastNameAscFirstNameAscIdAsc(7L))
				.thenReturn(List.of(ada, alice));
		TrainingImportRecognitionMapper mapper =
				mapper(athleteRepository);

		var draft = mapper.validateAndMap(recognition("""
				[{"recognizedName":"A. Lovelece","firstName":"A","lastName":"Lovelece",
				  "uncertain":false,"uncertaintyReason":null,"executions":%s}]
				""".formatted(executions())), 7L);

		assertEquals(TrainingImportAthleteMatchStatus.UNRESOLVED,
				draft.definitions().getFirst().athleteRows().getFirst().matchStatus());
	}

	@Test
	void leavesApproximateSurnameMatchesUnresolvedWhenDifferentSurnamesAreClose() {
		Athlete lovelace = athlete(41L, "Ada", "Lovelace");
		Athlete lovelece = athlete(42L, "Ben", "Lovelece");
		AthleteRepository athleteRepository = mock(AthleteRepository.class);
		when(athleteRepository.findByTrainingGroup_IdOrderByLastNameAscFirstNameAscIdAsc(7L))
				.thenReturn(List.of(lovelace, lovelece));
		TrainingImportRecognitionMapper mapper =
				mapper(athleteRepository);

		var draft = mapper.validateAndMap(recognition("""
				[{"recognizedName":"Alex Lovelece","firstName":"Alex","lastName":"Lovelece",
				  "uncertain":false,"uncertaintyReason":null,"executions":%s}]
				""".formatted(executions())), 7L);

		assertEquals(TrainingImportAthleteMatchStatus.UNRESOLVED,
				draft.definitions().getFirst().athleteRows().getFirst().matchStatus());
	}

	@Test
	void normalizesSupportedHandwrittenTimeSeparators() {
		TrainingImportRecognitionMapper mapper =
				mapper(mock(AthleteRepository.class));
		for (var sample : List.of(
				new TimeSample("59", "59.00"),
				new TimeSample("1:14", "74.00"),
				new TimeSample("55,1", "55.10"),
				new TimeSample("1,11,11", "71.11"),
				new TimeSample("1:11:11", "71.11"),
				new TimeSample("1 22,33", "82.33"),
				new TimeSample("1;11,11", "71.11"))) {
			String input = recognition("""
					[{"recognizedName":"Runner Name","firstName":"Runner","lastName":"Name",
					  "uncertain":false,"uncertaintyReason":null,"executions":%s}]
					""".formatted(executions().replace("1:02.3", sample.input())));

			var draft = mapper.validateAndMap(input, 7L);
			assertEquals(new java.math.BigDecimal(sample.expected()),
					draft.definitions().getFirst().athleteRows().getFirst()
							.executions().getFirst().measuredResult(),
					sample.input());
		}
	}

	private String recognition(String athleteRows) {
		return """
				{
				  "name":{"value":"Recognized session","originalText":"Session?","uncertain":true,
				          "uncertaintyReason":"Partly obscured"},
				  "trainingDate":{"value":"2026-11-10","originalText":"10.11.2026","uncertain":false,
				                  "uncertaintyReason":null},
				  "recognizedGroupName":"Not authoritative",
				  "definitions":[{
				    "sequenceNumber":1,
				    "name":{"value":"Main set","originalText":"Main set","uncertain":false,"uncertaintyReason":null},
				    "segments":[
				      {"sequenceNumber":1,"distanceM":{"value":"400","originalText":"400?","uncertain":true,
				        "uncertaintyReason":"Unclear digit"},"restAfterSeconds":{"value":"60","originalText":"60",
				        "uncertain":false,"uncertaintyReason":null}},
				      {"sequenceNumber":2,"distanceM":{"value":"200","originalText":"200","uncertain":false,
				        "uncertaintyReason":null},"restAfterSeconds":{"value":null,"originalText":null,
				        "uncertain":false,"uncertaintyReason":null}},
				      {"sequenceNumber":3,"distanceM":{"value":"100","originalText":"100","uncertain":false,
				        "uncertaintyReason":null},"restAfterSeconds":{"value":null,"originalText":null,
				        "uncertain":false,"uncertaintyReason":null}},
				      {"sequenceNumber":4,"distanceM":{"value":"50","originalText":"50","uncertain":false,
				        "uncertaintyReason":null},"restAfterSeconds":{"value":null,"originalText":null,
				        "uncertain":false,"uncertaintyReason":null}}
				    ],
				    "athleteRows":%s
				  }]
				}
				""".formatted(athleteRows);
	}

	private String executions() {
		return """
				[
				  {"segmentSequenceNumber":1,"recognizedText":"1:02.3","status":"RECORDED",
				   "measuredResult":"1:02.3","usesSpikes":true,"uncertain":true,"uncertaintyReason":"Crossed out"},
				  {"segmentSequenceNumber":2,"recognizedText":"X","status":"COMPLETED_WITHOUT_RESULT",
				   "measuredResult":null,"usesSpikes":false,"uncertain":false,"uncertaintyReason":null},
				  {"segmentSequenceNumber":3,"recognizedText":"— K","status":"NOT_COMPLETED",
				   "measuredResult":null,"usesSpikes":true,"uncertain":false,"uncertaintyReason":null},
				  {"segmentSequenceNumber":4,"recognizedText":"K","status":"PENDING",
				   "measuredResult":null,"usesSpikes":true,"uncertain":false,"uncertaintyReason":null}
				]
				""";
	}

	private String execution(String status, String measuredResult) {
		return """
				{"segmentSequenceNumber":1,"recognizedText":"12.3","status":"%s",
				 "measuredResult":%s,"usesSpikes":false,"uncertain":false,"uncertaintyReason":null}
				""".formatted(status, measuredResult);
	}

	private Athlete athlete(long id, String firstName, String lastName) {
		Athlete athlete = mock(Athlete.class);
		when(athlete.getId()).thenReturn(id);
		when(athlete.getFirstName()).thenReturn(firstName);
		when(athlete.getLastName()).thenReturn(lastName);
		return athlete;
	}

	private record TimeSample(String input, String expected) {
	}
}

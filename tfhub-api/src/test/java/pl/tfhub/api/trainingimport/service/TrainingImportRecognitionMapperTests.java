package pl.tfhub.api.trainingimport.service;

import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import pl.tfhub.api.athlete.entity.Athlete;
import pl.tfhub.api.athlete.repository.AthleteRepository;
import pl.tfhub.api.training.enums.TrainingExecutionStatus;
import pl.tfhub.api.trainingimport.enums.TrainingImportAthleteMatchStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TrainingImportRecognitionMapperTests {

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
				new TrainingImportRecognitionMapper(new ObjectMapper(), athleteRepository);

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
		assertEquals(TrainingImportAthleteMatchStatus.MATCHED, rows.get(0).matchStatus());
		assertEquals(41L, rows.get(0).athleteId());
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
				new TrainingImportRecognitionMapper(new ObjectMapper(), mock(AthleteRepository.class));
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
}

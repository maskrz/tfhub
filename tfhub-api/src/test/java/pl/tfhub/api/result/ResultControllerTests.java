package pl.tfhub.api.result;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pl.tfhub.api.athlete.AthleteNotFoundException;
import pl.tfhub.api.result.dto.CreateResultRequest;
import pl.tfhub.api.result.dto.CreateMultipleResultsRequest;
import pl.tfhub.api.result.dto.CreateMultipleResultsResponse;
import pl.tfhub.api.result.dto.CreateResultEntryRequest;
import pl.tfhub.api.result.dto.ResultResponse;
import pl.tfhub.api.result.MultipleResultsDuplicateAthleteException;
import pl.tfhub.api.test.TestCategory;
import pl.tfhub.api.test.TestNotFoundException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ResultController.class)
class ResultControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private ResultService resultService;

	@Test
	void retrievesResultAsResponseDto() throws Exception {
		when(resultService.getResult(32L)).thenReturn(new ResultResponse(
				32L, 7L, 2L, LocalDate.parse("2026-11-05"), new BigDecimal("5.420"), ResultType.TRAINING));

		mockMvc.perform(get("/api/results/32"))
				.andExpect(status().isOk())
				.andExpect(content().json("""
						{"id":32,"athleteId":7,"testId":2,"resultDate":"2026-11-05",
						"value":5.420,"type":"TRAINING"}
						""", true))
				.andExpect(jsonPath("$.class").doesNotExist());

		verify(resultService).getResult(32L);
	}

	@Test
	void returnsNotFoundWhenResultDoesNotExist() throws Exception {
		when(resultService.getResult(404L)).thenThrow(new ResultNotFoundException(404L));

		mockMvc.perform(get("/api/results/404"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.title").value("Result not found"));
	}

	@Test
	void rejectsInvalidResultIds() throws Exception {
		mockMvc.perform(get("/api/results/0"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.title").value("Invalid result ID"));
		mockMvc.perform(get("/api/results/-1"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.title").value("Invalid result ID"));
		mockMvc.perform(get("/api/results/not-a-number"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void createsResultAndReturnsLocationAndResponseDto() throws Exception {
		CreateResultRequest request = new CreateResultRequest(
				1L, 2L, LocalDate.parse("2026-11-05"), new BigDecimal("5.420"), ResultType.TRAINING);
		when(resultService.createResult(request)).thenReturn(new ResultResponse(
				32L, 1L, 2L, LocalDate.parse("2026-11-05"), new BigDecimal("5.420"), ResultType.TRAINING));

		mockMvc.perform(post("/api/results")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"athleteId":1,"testId":2,"resultDate":"2026-11-05",
								"value":5.420,"type":"TRAINING"}
								"""))
				.andExpect(status().isCreated())
				.andExpect(header().string("Location", "/api/results/32"))
				.andExpect(content().json("""
						{"id":32,"athleteId":1,"testId":2,"resultDate":"2026-11-05",
						"value":5.420,"type":"TRAINING"}
						""", true))
				.andExpect(jsonPath("$.class").doesNotExist());

		verify(resultService).createResult(request);
	}

	@Test
	void acceptsCompetitionResult() throws Exception {
		CreateResultRequest request = new CreateResultRequest(
				1L, 2L, LocalDate.parse("2026-11-05"), new BigDecimal("8.75"), ResultType.COMPETITION);
		when(resultService.createResult(request)).thenReturn(new ResultResponse(
				33L, 1L, 2L, LocalDate.parse("2026-11-05"), new BigDecimal("8.75"), ResultType.COMPETITION));

		mockMvc.perform(post("/api/results")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"athleteId":1,"testId":2,"resultDate":"2026-11-05",
								"value":8.75,"type":"COMPETITION"}
								"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.type").value("COMPETITION"));
	}

	@Test
	void createsMultipleResultsInRequestOrderWithoutLocationHeader() throws Exception {
		CreateMultipleResultsRequest request = new CreateMultipleResultsRequest(
				2L,
				LocalDate.parse("2026-11-05"),
				ResultType.TRAINING,
				List.of(
						new CreateResultEntryRequest(7L, new BigDecimal("5.420")),
						new CreateResultEntryRequest(3L, new BigDecimal("5.300"))));
		when(resultService.createMultipleResults(request)).thenReturn(new CreateMultipleResultsResponse(2, List.of(
				new ResultResponse(41L, 7L, 2L, LocalDate.parse("2026-11-05"),
						new BigDecimal("5.420"), ResultType.TRAINING),
				new ResultResponse(42L, 3L, 2L, LocalDate.parse("2026-11-05"),
						new BigDecimal("5.300"), ResultType.TRAINING))));

		mockMvc.perform(post("/api/results/batch")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"testId":2,"resultDate":"2026-11-05","type":"TRAINING",
								"results":[{"athleteId":7,"value":5.420},{"athleteId":3,"value":5.300}]}
								"""))
				.andExpect(status().isCreated())
				.andExpect(header().doesNotExist("Location"))
				.andExpect(content().json("""
						{"createdCount":2,"results":[
						{"id":41,"athleteId":7,"testId":2,"resultDate":"2026-11-05",
						"value":5.420,"type":"TRAINING"},
						{"id":42,"athleteId":3,"testId":2,"resultDate":"2026-11-05",
						"value":5.300,"type":"TRAINING"}]}
						""", true))
				.andExpect(jsonPath("$.results[0].athleteId").value(7))
				.andExpect(jsonPath("$.results[1].athleteId").value(3));

		verify(resultService).createMultipleResults(request);
	}

	@Test
	void mapsMissingAthleteAndTestToNotFoundAndInactiveTestToConflict() throws Exception {
		CreateResultRequest request = new CreateResultRequest(
				1L, 2L, LocalDate.parse("2026-11-05"), new BigDecimal("5.42"), ResultType.TRAINING);
		doThrow(new AthleteNotFoundException(1)).when(resultService).createResult(request);
		mockMvc.perform(post("/api/results").contentType(MediaType.APPLICATION_JSON).content(validBody()))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.title").value("Athlete not found"));

		doThrow(new TestNotFoundException(2)).when(resultService).createResult(request);
		mockMvc.perform(post("/api/results").contentType(MediaType.APPLICATION_JSON).content(validBody()))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.title").value("Test not found"));

		doThrow(new InactiveTestException(2)).when(resultService).createResult(request);
		mockMvc.perform(post("/api/results").contentType(MediaType.APPLICATION_JSON).content(validBody()))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.title").value("Test is inactive"));
	}

	@Test
	void rejectsMissingInvalidAndMalformedRequestFields() throws Exception {
		assertBadRequest("""
				{"testId":2,"resultDate":"2026-11-05","value":5.42,"type":"TRAINING"}
				""");
		assertBadRequest("""
				{"athleteId":1,"resultDate":"2026-11-05","value":5.42,"type":"TRAINING"}
				""");
		assertBadRequest("""
				{"athleteId":1,"testId":2,"value":5.42,"type":"TRAINING"}
				""");
		assertBadRequest("""
				{"athleteId":1,"testId":2,"resultDate":"2026-11-05","type":"TRAINING"}
				""");
		assertBadRequest("""
				{"athleteId":1,"testId":2,"resultDate":"2026-11-05","value":5.42}
				""");
		assertBadRequest("""
				{"athleteId":0,"testId":2,"resultDate":"2026-11-05","value":5.42,"type":"TRAINING"}
				""");
		assertBadRequest("""
				{"athleteId":1,"testId":-2,"resultDate":"2026-11-05","value":5.42,"type":"TRAINING"}
				""");
		assertBadRequest("""
				{"athleteId":1.5,"testId":2,"resultDate":"2026-11-05","value":5.42,"type":"TRAINING"}
				""");
		assertBadRequest("""
				{"athleteId":1,"testId":2,"resultDate":"05-11-2026","value":5.42,"type":"TRAINING"}
				""");
		assertBadRequest("""
				{"athleteId":1,"testId":2,"resultDate":"2026-11-05","value":0,"type":"TRAINING"}
				""");
		assertBadRequest("""
				{"athleteId":1,"testId":2,"resultDate":"2026-11-05","value":-1,"type":"TRAINING"}
				""");
		assertBadRequest("""
				{"athleteId":1,"testId":2,"resultDate":"2026-11-05","value":1.2345,"type":"TRAINING"}
				""");
		assertBadRequest("""
				{"athleteId":1,"testId":2,"resultDate":"2026-11-05","value":1000000000,"type":"TRAINING"}
				""");
		assertBadRequest("""
				{"athleteId":1,"testId":2,"resultDate":"2026-11-05","value":"5.42","type":"TRAINING"}
				""");
		assertBadRequest("""
				{"athleteId":1,"testId":2,"resultDate":"2026-11-05","value":5.42,"type":"OTHER"}
				""");
		assertBadRequest("{\"athleteId\":");
	}

	@Test
	void rejectsInvalidBatchStructureAndEntries() throws Exception {
		assertBatchBadRequest("""
				{"testId":2,"resultDate":"2026-11-05","type":"TRAINING","results":[]}
				""");
		assertBatchBadRequest("""
				{"testId":0,"resultDate":"2026-11-05","type":"TRAINING",
				"results":[{"athleteId":1,"value":5}]}
				""");
		assertBatchBadRequest("""
				{"testId":2,"resultDate":"2026-11-05","type":"OTHER",
				"results":[{"athleteId":1,"value":5}]}
				""");
		assertBatchBadRequest("""
				{"testId":2,"resultDate":"2026-11-05","type":"TRAINING",
				"results":[{"athleteId":0,"value":5}]}
				""");
		assertBatchBadRequest("""
				{"testId":2,"resultDate":"2026-11-05","type":"TRAINING",
				"results":[{"athleteId":1,"value":0}]}
				""");
		assertBatchBadRequest("""
				{"testId":2,"resultDate":"2026-11-05","type":"TRAINING",
				"results":[{"athleteId":1,"value":-0.1}]}
				""");
		assertBatchBadRequest("""
				{"testId":2,"resultDate":"2026-11-05","type":"TRAINING",
				"results":[{"athleteId":1,"value":1.2345}]}
				""");
		assertBatchBadRequest("""
				{"testId":2,"resultDate":"2026-11-05","type":"TRAINING",
				"results":[{"athleteId":1,"value":1000000000}]}
				""");
		assertBatchBadRequest("""
				{"testId":2,"resultDate":"2026-11-05","type":"TRAINING",
				"results":[{"athleteId":1,"value":"5.4"}]}
				""");
		assertBatchBadRequest("{\"testId\":2,");
	}

	@Test
	void rejectsBatchWithMoreThanOneHundredEntries() throws Exception {
		String entries = java.util.stream.IntStream.rangeClosed(1, 101)
				.mapToObj(id -> "{\"athleteId\":" + id + ",\"value\":5}")
				.collect(java.util.stream.Collectors.joining(","));
		assertBatchBadRequest("""
				{"testId":2,"resultDate":"2026-11-05","type":"TRAINING","results":[%s]}
				""".formatted(entries));
	}

	@Test
	void rejectsMissingBatchFieldsAndMissingEntryFields() throws Exception {
		assertBatchBadRequest("""
				{"resultDate":"2026-11-05","type":"TRAINING",
				"results":[{"athleteId":1,"value":5}]}
				""");
		assertBatchBadRequest("""
				{"testId":2,"type":"TRAINING","results":[{"athleteId":1,"value":5}]}
				""");
		assertBatchBadRequest("""
				{"testId":2,"resultDate":"2026-11-05",
				"results":[{"athleteId":1,"value":5}]}
				""");
		assertBatchBadRequest("""
				{"testId":2,"resultDate":"2026-11-05","type":"TRAINING"}
				""");
		assertBatchBadRequest("""
				{"testId":2,"resultDate":"2026-11-05","type":"TRAINING",
				"results":[{"value":5}]}
				""");
		assertBatchBadRequest("""
				{"testId":2,"resultDate":"2026-11-05","type":"TRAINING",
				"results":[{"athleteId":1}]}
				""");
		assertBatchBadRequest("""
				{"testId":2,"resultDate":"2026-11-05","type":"TRAINING",
				"results":[{"athleteId":1,"value":5},{"athleteId":2,"value":1.2345}]}
				""");
	}

	@Test
	void duplicateAthleteIdsReturnBadRequest() throws Exception {
		CreateMultipleResultsRequest request = new CreateMultipleResultsRequest(
				2L, LocalDate.parse("2026-11-05"), ResultType.TRAINING,
				List.of(
						new CreateResultEntryRequest(7L, new BigDecimal("5.420")),
						new CreateResultEntryRequest(7L, new BigDecimal("5.300"))));
		when(resultService.createMultipleResults(request))
				.thenThrow(new MultipleResultsDuplicateAthleteException());

		mockMvc.perform(post("/api/results/batch")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"testId":2,"resultDate":"2026-11-05","type":"TRAINING",
								"results":[{"athleteId":7,"value":5.420},{"athleteId":7,"value":5.300}]}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.title").value("Invalid result batch"));
	}

	private void assertBadRequest(String body) throws Exception {
		mockMvc.perform(post("/api/results")
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isBadRequest());
	}

	private void assertBatchBadRequest(String body) throws Exception {
		mockMvc.perform(post("/api/results/batch")
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isBadRequest());
	}

	private String validBody() {
		return """
				{"athleteId":1,"testId":2,"resultDate":"2026-11-05","value":5.42,"type":"TRAINING"}
				""";
	}
}

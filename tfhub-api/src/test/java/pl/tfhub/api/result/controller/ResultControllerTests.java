package pl.tfhub.api.result.controller;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pl.tfhub.api.athlete.entity.Athlete;
import pl.tfhub.api.athlete.exception.AthleteNotFoundException;
import pl.tfhub.api.result.dto.request.CreateMultipleResultsRequest;
import pl.tfhub.api.result.dto.request.CreateResultEntryRequest;
import pl.tfhub.api.result.dto.request.CreateResultRequest;
import pl.tfhub.api.result.dto.request.UpdateResultRequest;
import pl.tfhub.api.result.dto.response.CreateMultipleResultsResponse;
import pl.tfhub.api.result.dto.response.ResultResponse;
import pl.tfhub.api.result.enums.ResultType;
import pl.tfhub.api.result.exception.InactiveAthleteException;
import pl.tfhub.api.result.exception.InactiveTestException;
import pl.tfhub.api.result.exception.InvalidResultSearchException;
import pl.tfhub.api.result.exception.MultipleResultsDuplicateAthleteException;
import pl.tfhub.api.result.exception.ResultNotFoundException;
import pl.tfhub.api.result.service.ResultService;
import pl.tfhub.api.test.enums.TestCategory;
import pl.tfhub.api.test.exception.TestNotFoundException;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
	void deletesResultAndReturnsNoContentWithEmptyBody() throws Exception {
		mockMvc.perform(delete("/api/results/32"))
				.andExpect(status().isNoContent())
				.andExpect(content().string(""));

		verify(resultService).deleteResult(32L);
	}

	@Test
	void returnsNotFoundWhenDeletingNonexistentResult() throws Exception {
		doThrow(new ResultNotFoundException(404L)).when(resultService).deleteResult(404L);

		mockMvc.perform(delete("/api/results/404"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.title").value("Result not found"));
	}

	@Test
	void rejectsInvalidResultIdsForDeletion() throws Exception {
		mockMvc.perform(delete("/api/results/0"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.title").value("Invalid result ID"));
		mockMvc.perform(delete("/api/results/-1"))
				.andExpect(status().isBadRequest());
		mockMvc.perform(delete("/api/results/not-a-number"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void updatesResultAndReturnsUpdatedResponseWithSameId() throws Exception {
		UpdateResultRequest request = updateRequest();
		when(resultService.updateResult(32L, request)).thenReturn(new ResultResponse(
				32L, 8L, 3L, LocalDate.parse("2026-11-06"), new BigDecimal("6.125"), ResultType.COMPETITION));

		mockMvc.perform(put("/api/results/32")
						.contentType(MediaType.APPLICATION_JSON)
						.content(validUpdateBody()))
				.andExpect(status().isOk())
				.andExpect(content().json("""
						{"id":32,"athleteId":8,"testId":3,"resultDate":"2026-11-06",
						"value":6.125,"type":"COMPETITION"}
						""", true))
				.andExpect(jsonPath("$.class").doesNotExist());

		verify(resultService).updateResult(32L, request);
	}

	@Test
	void returnsNotFoundWhenResultUpdateTargetDoesNotExist() throws Exception {
		when(resultService.updateResult(404L, updateRequest()))
				.thenThrow(new ResultNotFoundException(404L));

		mockMvc.perform(put("/api/results/404")
						.contentType(MediaType.APPLICATION_JSON)
						.content(validUpdateBody()))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.title").value("Result not found"));
	}

	@Test
	void mapsMissingAthleteAndTestToNotFoundAndInactiveTestToConflictForUpdates() throws Exception {
		UpdateResultRequest request = updateRequest();
		doThrow(new AthleteNotFoundException(8)).when(resultService).updateResult(32L, request);
		mockMvc.perform(put("/api/results/32")
						.contentType(MediaType.APPLICATION_JSON)
						.content(validUpdateBody()))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.title").value("Athlete not found"));

		doThrow(new TestNotFoundException(3)).when(resultService).updateResult(32L, request);
		mockMvc.perform(put("/api/results/32")
						.contentType(MediaType.APPLICATION_JSON)
						.content(validUpdateBody()))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.title").value("Test not found"));

		doThrow(new InactiveTestException(3)).when(resultService).updateResult(32L, request);
		mockMvc.perform(put("/api/results/32")
						.contentType(MediaType.APPLICATION_JSON)
						.content(validUpdateBody()))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.title").value("Test is inactive"));
	}

	@Test
	void rejectsInvalidUpdateResultIds() throws Exception {
		mockMvc.perform(put("/api/results/0")
						.contentType(MediaType.APPLICATION_JSON)
						.content(validUpdateBody()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.title").value("Invalid result ID"));
		mockMvc.perform(put("/api/results/-1")
						.contentType(MediaType.APPLICATION_JSON)
						.content(validUpdateBody()))
				.andExpect(status().isBadRequest());
		mockMvc.perform(put("/api/results/not-a-number")
						.contentType(MediaType.APPLICATION_JSON)
						.content(validUpdateBody()))
				.andExpect(status().isBadRequest());
	}

	@Test
	void rejectsMissingInvalidAndMalformedUpdateRequestFields() throws Exception {
		assertUpdateBadRequest("""
				{"testId":2,"resultDate":"2026-11-05","value":5.42,"type":"TRAINING"}
				""");
		assertUpdateBadRequest("""
				{"athleteId":1,"resultDate":"2026-11-05","value":5.42,"type":"TRAINING"}
				""");
		assertUpdateBadRequest("""
				{"athleteId":1,"testId":2,"value":5.42,"type":"TRAINING"}
				""");
		assertUpdateBadRequest("""
				{"athleteId":1,"testId":2,"resultDate":"2026-11-05","type":"TRAINING"}
				""");
		assertUpdateBadRequest("""
				{"athleteId":1,"testId":2,"resultDate":"2026-11-05","value":5.42}
				""");
		assertUpdateBadRequest("""
				{"athleteId":0,"testId":2,"resultDate":"2026-11-05","value":5.42,"type":"TRAINING"}
				""");
		assertUpdateBadRequest("""
				{"athleteId":1,"testId":-2,"resultDate":"2026-11-05","value":5.42,"type":"TRAINING"}
				""");
		assertUpdateBadRequest("""
				{"athleteId":1,"testId":2,"resultDate":"2026-11-05","value":0,"type":"TRAINING"}
				""");
		assertUpdateBadRequest("""
				{"athleteId":1,"testId":2,"resultDate":"2026-11-05","value":-1,"type":"TRAINING"}
				""");
		assertUpdateBadRequest("""
				{"athleteId":1,"testId":2,"resultDate":"2026-11-05","value":1.2345,"type":"TRAINING"}
				""");
		assertUpdateBadRequest("""
				{"athleteId":1,"testId":2,"resultDate":"2026-11-05","value":1000000000,"type":"TRAINING"}
				""");
		assertUpdateBadRequest("""
				{"athleteId":1,"testId":2,"resultDate":"2026-11-05","value":5.42,"type":"OTHER"}
				""");
		assertUpdateBadRequest("""
				{"athleteId":1,"testId":2,"resultDate":"05-11-2026","value":5.42,"type":"TRAINING"}
				""");
		assertUpdateBadRequest("{\"athleteId\":");
	}

	@Test
	void listsResultsWithoutFiltersAndReturnsAnEmptyJsonArrayWhenNoneMatch() throws Exception {
		when(resultService.searchResults(null, null, null, null, null)).thenReturn(List.of());

		mockMvc.perform(get("/api/results"))
				.andExpect(status().isOk())
				.andExpect(content().json("[]", true));

		verify(resultService).searchResults(null, null, null, null, null);
	}

	@Test
	void listsResultsWithAllFiltersCombined() throws Exception {
		ResultResponse result = new ResultResponse(
				32L, 7L, 2L, LocalDate.parse("2026-11-05"), new BigDecimal("5.420"), ResultType.TRAINING);
		when(resultService.searchResults(
				7L, 2L, LocalDate.parse("2026-11-01"), LocalDate.parse("2026-11-30"), ResultType.TRAINING))
				.thenReturn(List.of(result));

		mockMvc.perform(get("/api/results")
						.param("athleteId", "7")
						.param("testId", "2")
						.param("fromDate", "2026-11-01")
						.param("toDate", "2026-11-30")
						.param("type", "TRAINING"))
				.andExpect(status().isOk())
				.andExpect(content().json("""
						[{"id":32,"athleteId":7,"testId":2,"resultDate":"2026-11-05",
						"value":5.420,"type":"TRAINING"}]
						""", true))
				.andExpect(jsonPath("$[0].class").doesNotExist());

		verify(resultService).searchResults(
				7L, 2L, LocalDate.parse("2026-11-01"), LocalDate.parse("2026-11-30"), ResultType.TRAINING);
	}

	@Test
	void acceptsEachResultSearchFilterIndividually() throws Exception {
		when(resultService.searchResults(7L, null, null, null, null)).thenReturn(List.of());
		when(resultService.searchResults(null, 2L, null, null, null)).thenReturn(List.of());
		when(resultService.searchResults(null, null, LocalDate.parse("2026-11-01"), null, null))
				.thenReturn(List.of());
		when(resultService.searchResults(null, null, null, LocalDate.parse("2026-11-30"), null))
				.thenReturn(List.of());
		when(resultService.searchResults(null, null, null, null, ResultType.COMPETITION)).thenReturn(List.of());

		mockMvc.perform(get("/api/results").param("athleteId", "7")).andExpect(status().isOk());
		mockMvc.perform(get("/api/results").param("testId", "2")).andExpect(status().isOk());
		mockMvc.perform(get("/api/results").param("fromDate", "2026-11-01")).andExpect(status().isOk());
		mockMvc.perform(get("/api/results").param("toDate", "2026-11-30")).andExpect(status().isOk());
		mockMvc.perform(get("/api/results").param("type", "COMPETITION")).andExpect(status().isOk());

		verify(resultService).searchResults(7L, null, null, null, null);
		verify(resultService).searchResults(null, 2L, null, null, null);
		verify(resultService).searchResults(null, null, LocalDate.parse("2026-11-01"), null, null);
		verify(resultService).searchResults(null, null, null, LocalDate.parse("2026-11-30"), null);
		verify(resultService).searchResults(null, null, null, null, ResultType.COMPETITION);
	}

	@Test
	void listsResultsForAnUnknownButValidAthleteOrTestAsAnEmptyArray() throws Exception {
		when(resultService.searchResults(999L, null, null, null, null)).thenReturn(List.of());
		when(resultService.searchResults(null, 998L, null, null, null)).thenReturn(List.of());

		mockMvc.perform(get("/api/results").param("athleteId", "999"))
				.andExpect(status().isOk())
				.andExpect(content().json("[]", true));
		mockMvc.perform(get("/api/results").param("testId", "998"))
				.andExpect(status().isOk())
				.andExpect(content().json("[]", true));
	}

	@Test
	void rejectsInvalidResultSearchParameters() throws Exception {
		mockMvc.perform(get("/api/results").param("athleteId", "0"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.title").value("Invalid result search"));
		mockMvc.perform(get("/api/results").param("athleteId", "-1"))
				.andExpect(status().isBadRequest());
		mockMvc.perform(get("/api/results").param("testId", "not-a-number"))
				.andExpect(status().isBadRequest());
		mockMvc.perform(get("/api/results").param("fromDate", "05-11-2026"))
				.andExpect(status().isBadRequest());
		mockMvc.perform(get("/api/results").param("toDate", "2026-02-30"))
				.andExpect(status().isBadRequest());
		mockMvc.perform(get("/api/results").param("type", "OTHER"))
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

		doThrow(new InactiveAthleteException(1)).when(resultService).createResult(request);
		mockMvc.perform(post("/api/results").contentType(MediaType.APPLICATION_JSON).content(validBody()))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.title").value("Athlete is inactive"));

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

	private void assertUpdateBadRequest(String body) throws Exception {
		mockMvc.perform(put("/api/results/32")
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

	private UpdateResultRequest updateRequest() {
		return new UpdateResultRequest(
				8L, 3L, LocalDate.parse("2026-11-06"), new BigDecimal("6.125"), ResultType.COMPETITION);
	}

	private String validUpdateBody() {
		return """
				{"athleteId":8,"testId":3,"resultDate":"2026-11-06","value":6.125,"type":"COMPETITION"}
				""";
	}
}

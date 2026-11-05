package pl.tfhub.api.result;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pl.tfhub.api.athlete.AthleteNotFoundException;
import pl.tfhub.api.result.dto.CreateResultRequest;
import pl.tfhub.api.result.dto.ResultResponse;
import pl.tfhub.api.test.TestCategory;
import pl.tfhub.api.test.TestNotFoundException;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

	private void assertBadRequest(String body) throws Exception {
		mockMvc.perform(post("/api/results")
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

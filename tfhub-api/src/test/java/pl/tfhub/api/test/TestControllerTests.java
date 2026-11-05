package pl.tfhub.api.test;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pl.tfhub.api.test.dto.CreateTestRequest;
import pl.tfhub.api.test.dto.TestSearchRequest;
import pl.tfhub.api.test.dto.TestResponse;
import pl.tfhub.api.test.dto.UpdateTestRequest;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TestController.class)
class TestControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private TestService testService;

	@Test
	void createsTimeTestAndReturnsLocationAndResponseDto() throws Exception {
		CreateTestRequest request = new CreateTestRequest("Sprint 40m", "s", TestCategory.TIME, false);
		when(testService.createTest(request))
				.thenReturn(new TestResponse(12L, "Sprint 40m", "s", TestCategory.TIME, false, true));

		mockMvc.perform(post("/api/tests")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name":" Sprint 40m ","unit":"s","category":"TIME","higherIsBetter":false}
								"""))
				.andExpect(status().isCreated())
				.andExpect(header().string("Location", "/api/tests/12"))
				.andExpect(content().json("""
						{"id":12,"name":"Sprint 40m","unit":"s","category":"TIME",
						"higherIsBetter":false,"active":true}
						""", true))
				.andExpect(jsonPath("$.class").doesNotExist());

		verify(testService).createTest(request);
	}

	@Test
	void createsDistanceAndSpeedTestsAndPreservesHigherIsBetter() throws Exception {
		CreateTestRequest distanceRequest =
				new CreateTestRequest("Long jump", "m", TestCategory.DISTANCE, true);
		CreateTestRequest speedRequest =
				new CreateTestRequest("Running speed", "m/s", TestCategory.SPEED, true);
		when(testService.createTest(distanceRequest))
				.thenReturn(new TestResponse(13L, "Long jump", "m", TestCategory.DISTANCE, true, true));
		when(testService.createTest(speedRequest))
				.thenReturn(new TestResponse(14L, "Running speed", "m/s", TestCategory.SPEED, true, true));

		mockMvc.perform(post("/api/tests").contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name":"Long jump","unit":"m","category":"DISTANCE","higherIsBetter":true}
								"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.category").value("DISTANCE"))
				.andExpect(jsonPath("$.higherIsBetter").value(true))
				.andExpect(jsonPath("$.active").value(true));
		mockMvc.perform(post("/api/tests").contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name":"Running speed","unit":"m/s","category":"SPEED","higherIsBetter":true}
								"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.category").value("SPEED"))
				.andExpect(jsonPath("$.higherIsBetter").value(true))
				.andExpect(jsonPath("$.active").value(true));

		verify(testService).createTest(distanceRequest);
		verify(testService).createTest(speedRequest);
	}

	@Test
	void rejectsMissingOrInvalidFieldsAndMalformedJson() throws Exception {
		assertBadRequest("""
				{"unit":"s","category":"TIME","higherIsBetter":false}
				""");
		assertBadRequest("""
				{"name":"  ","unit":"s","category":"TIME","higherIsBetter":false}
				""");
		assertBadRequest("""
				{"name":"%s","unit":"s","category":"TIME","higherIsBetter":false}
				""".formatted("x".repeat(151)));
		assertBadRequest("""
				{"name":"Sprint","category":"TIME","higherIsBetter":false}
				""");
		assertBadRequest("""
				{"name":"Sprint","unit":"km","category":"TIME","higherIsBetter":false}
				""");
		assertBadRequest("""
				{"name":"Sprint","unit":"s","category":"OTHER","higherIsBetter":false}
				""");
		assertBadRequest("""
				{"name":"Sprint","unit":"s","higherIsBetter":false}
				""");
		assertBadRequest("""
				{"name":"Sprint","unit":"s","category":"TIME"}
				""");
		assertBadRequest("""
				{"name":"Sprint","unit":"s","category":"TIME","higherIsBetter":"false"}
				""");
		assertBadRequest("{\"name\":");
	}

	@Test
	void returnsConflictForDuplicateTestName() throws Exception {
		CreateTestRequest request = new CreateTestRequest("Sprint", "s", TestCategory.TIME, false);
		when(testService.createTest(request)).thenThrow(new TestAlreadyExistsException());

		mockMvc.perform(post("/api/tests")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name":"Sprint","unit":"s","category":"TIME","higherIsBetter":false}
								"""))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.title").value("Test already exists"));
	}

	@Test
	void retrievesActiveTestAsResponseDto() throws Exception {
		when(testService.getTest(12L))
				.thenReturn(new TestResponse(12L, "Sprint 40m", "s", TestCategory.TIME, false, true));

		mockMvc.perform(get("/api/tests/12"))
				.andExpect(status().isOk())
				.andExpect(content().json("""
						{"id":12,"name":"Sprint 40m","unit":"s","category":"TIME",
						"higherIsBetter":false,"active":true}
						""", true))
				.andExpect(jsonPath("$.class").doesNotExist());

		verify(testService).getTest(12L);
	}

	@Test
	void retrievesInactiveTestAsResponseDto() throws Exception {
		when(testService.getTest(13L))
				.thenReturn(new TestResponse(13L, "Long jump", "m", TestCategory.DISTANCE, true, false));

		mockMvc.perform(get("/api/tests/13"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(13))
				.andExpect(jsonPath("$.name").value("Long jump"))
				.andExpect(jsonPath("$.unit").value("m"))
				.andExpect(jsonPath("$.category").value("DISTANCE"))
				.andExpect(jsonPath("$.higherIsBetter").value(true))
				.andExpect(jsonPath("$.active").value(false));
	}

	@Test
	void returnsNotFoundWhenTestDoesNotExist() throws Exception {
		when(testService.getTest(404L)).thenThrow(new TestNotFoundException(404L));

		mockMvc.perform(get("/api/tests/404"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.title").value("Test not found"));
	}

	@Test
	void rejectsInvalidTestIds() throws Exception {
		mockMvc.perform(get("/api/tests/0"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.title").value("Invalid test ID"));
		mockMvc.perform(get("/api/tests/-1"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.title").value("Invalid test ID"));
		mockMvc.perform(get("/api/tests/not-a-number"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void updatesTestAndReturnsResponseDto() throws Exception {
		UpdateTestRequest request = new UpdateTestRequest("Sprint 40m", "m/s", TestCategory.SPEED, true);
		when(testService.updateTest(12L, request))
				.thenReturn(new TestResponse(12L, "Sprint 40m", "m/s", TestCategory.SPEED, true, true));

		mockMvc.perform(put("/api/tests/12")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name":" Sprint 40m ","unit":"m/s","category":"SPEED","higherIsBetter":true}
								"""))
				.andExpect(status().isOk())
				.andExpect(content().json("""
						{"id":12,"name":"Sprint 40m","unit":"m/s","category":"SPEED",
						"higherIsBetter":true,"active":true}
						""", true))
				.andExpect(jsonPath("$.class").doesNotExist());

		verify(testService).updateTest(12L, request);
	}

	@Test
	void returnsNotFoundAndConflictForUpdateFailures() throws Exception {
		UpdateTestRequest request = new UpdateTestRequest("Sprint", "s", TestCategory.TIME, false);
		when(testService.updateTest(404L, request)).thenThrow(new TestNotFoundException(404L));
		when(testService.updateTest(409L, request)).thenThrow(new TestAlreadyExistsException());
		when(testService.updateTest(410L, request)).thenThrow(new TestHasResultsException());

		mockMvc.perform(put("/api/tests/404").contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name":"Sprint","unit":"s","category":"TIME","higherIsBetter":false}
								"""))
				.andExpect(status().isNotFound());
		mockMvc.perform(put("/api/tests/409").contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name":"Sprint","unit":"s","category":"TIME","higherIsBetter":false}
								"""))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.title").value("Test already exists"));
		mockMvc.perform(put("/api/tests/410").contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name":"Sprint","unit":"s","category":"TIME","higherIsBetter":false}
								"""))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.title").value("Test configuration cannot be changed"));
	}

	@Test
	void rejectsInvalidUpdateIdsAndRequests() throws Exception {
		assertUpdateBadRequest("0", """
				{"name":"Sprint","unit":"s","category":"TIME","higherIsBetter":false}
				""");
		assertUpdateBadRequest("-1", """
				{"name":"Sprint","unit":"s","category":"TIME","higherIsBetter":false}
				""");
		assertUpdateBadRequest("not-a-number", """
				{"name":"Sprint","unit":"s","category":"TIME","higherIsBetter":false}
				""");
		assertUpdateBadRequest("1", """
				{"unit":"s","category":"TIME","higherIsBetter":false}
				""");
		assertUpdateBadRequest("1", """
				{"name":"   ","unit":"s","category":"TIME","higherIsBetter":false}
				""");
		assertUpdateBadRequest("1", """
				{"name":"%s","unit":"s","category":"TIME","higherIsBetter":false}
				""".formatted("x".repeat(151)));
		assertUpdateBadRequest("1", """
				{"name":"Sprint","unit":"km","category":"TIME","higherIsBetter":false}
				""");
		assertUpdateBadRequest("1", """
				{"name":"Sprint","unit":"s","category":"OTHER","higherIsBetter":false}
				""");
		assertUpdateBadRequest("1", """
				{"name":"Sprint","unit":"s","category":"TIME","higherIsBetter":"false"}
				""");
		assertUpdateBadRequest("1", "{\"name\":");
	}

	@Test
	void searchesByNameAndReturnsResponseDtos() throws Exception {
		TestSearchRequest request = new TestSearchRequest("sprint", null, null, null, null);
		when(testService.searchTests(request)).thenReturn(List.of(
				new TestResponse(12L, "Sprint 40m", "s", TestCategory.TIME, false, true)));

		mockMvc.perform(post("/api/tests/search")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name":"  sprint  "}
								"""))
				.andExpect(status().isOk())
				.andExpect(content().json("""
						[{"id":12,"name":"Sprint 40m","unit":"s","category":"TIME",
						"higherIsBetter":false,"active":true}]
						""", true))
				.andExpect(jsonPath("$[0].class").doesNotExist());

		verify(testService).searchTests(request);
	}

	@Test
	void acceptsEveryOptionalSearchCriterionIncludingFalseValues() throws Exception {
		TestSearchRequest request =
				new TestSearchRequest(null, "m", TestCategory.DISTANCE, false, false);
		when(testService.searchTests(request)).thenReturn(List.of(
				new TestResponse(13L, "Long jump", "m", TestCategory.DISTANCE, false, false)));

		mockMvc.perform(post("/api/tests/search")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"unit":"m","category":"DISTANCE","higherIsBetter":false,"active":false}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].unit").value("m"))
				.andExpect(jsonPath("$[0].category").value("DISTANCE"))
				.andExpect(jsonPath("$[0].higherIsBetter").value(false))
				.andExpect(jsonPath("$[0].active").value(false));

		verify(testService).searchTests(request);
	}

	@Test
	void emptySearchReturnsAllTestsIncludingInactiveAndNoMatchesReturnsEmptyArray() throws Exception {
		TestSearchRequest emptyRequest = new TestSearchRequest(null, null, null, null, null);
		when(testService.searchTests(emptyRequest)).thenReturn(List.of(
				new TestResponse(12L, "Sprint", "s", TestCategory.TIME, false, true),
				new TestResponse(13L, "Long jump", "m", TestCategory.DISTANCE, true, false)));

		mockMvc.perform(post("/api/tests/search")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[1].active").value(false));

		TestSearchRequest noMatchRequest = new TestSearchRequest("missing", null, null, null, null);
		when(testService.searchTests(noMatchRequest)).thenReturn(List.of());
		mockMvc.perform(post("/api/tests/search")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name":"missing"}
								"""))
				.andExpect(status().isOk())
				.andExpect(content().json("[]", true));
	}

	@Test
	void rejectsInvalidSearchUnitCategoryAndMalformedJson() throws Exception {
		assertSearchBadRequest("""
				{"unit":"km"}
				""");
		assertSearchBadRequest("""
				{"category":"OTHER"}
				""");
		assertSearchBadRequest("""
				{"higherIsBetter":"true"}
				""");
		assertSearchBadRequest("""
				{"active":1}
				""");
		assertSearchBadRequest("{\"name\":");
	}

	private void assertBadRequest(String body) throws Exception {
		mockMvc.perform(post("/api/tests")
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isBadRequest());
	}

	private void assertSearchBadRequest(String body) throws Exception {
		mockMvc.perform(post("/api/tests/search")
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isBadRequest());
	}

	private void assertUpdateBadRequest(String id, String body) throws Exception {
		mockMvc.perform(put("/api/tests/{id}", id)
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isBadRequest());
	}
}

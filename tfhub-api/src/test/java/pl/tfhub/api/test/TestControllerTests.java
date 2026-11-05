package pl.tfhub.api.test;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pl.tfhub.api.test.dto.CreateTestRequest;
import pl.tfhub.api.test.dto.TestResponse;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

	private void assertBadRequest(String body) throws Exception {
		mockMvc.perform(post("/api/tests")
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isBadRequest());
	}
}

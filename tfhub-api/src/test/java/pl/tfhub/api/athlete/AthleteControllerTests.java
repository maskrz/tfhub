package pl.tfhub.api.athlete;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pl.tfhub.api.athlete.dto.AthleteRequest;
import pl.tfhub.api.athlete.dto.AthleteResponse;

import java.time.Year;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AthleteController.class)
class AthleteControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private AthleteService athleteService;

	@Test
	void createsAthleteAndReturnsLocation() throws Exception {
		when(athleteService.createAthlete(any(AthleteRequest.class))).thenReturn(
				new AthleteResponse(42L, "John", "Smith", 2010, Gender.MALE, true));

		mockMvc.perform(post("/api/athletes")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"firstName":"  John  ","lastName":" Smith ","birthYear":2010,
								"gender":"MALE","id":999,"active":false}
								"""))
				.andExpect(status().isCreated())
				.andExpect(header().string("Location", "/api/athletes/42"))
				.andExpect(jsonPath("$.id").value(42))
				.andExpect(jsonPath("$.firstName").value("John"))
				.andExpect(jsonPath("$.lastName").value("Smith"))
				.andExpect(jsonPath("$.active").value(true));

		verify(athleteService).createAthlete(new AthleteRequest("John", "Smith", 2010, Gender.MALE));
	}

	@Test
	void rejectsMissingOrBlankFields() throws Exception {
		assertBadRequest("""
				{"lastName":"Smith","birthYear":2010,"gender":"MALE"}
				""");
		assertBadRequest("""
				{"firstName":"   ","lastName":"Smith","birthYear":2010,"gender":"MALE"}
				""");
		assertBadRequest("""
				{"firstName":"John","lastName":"Smith","gender":"MALE"}
				""");
		assertBadRequest("""
				{"firstName":"John","lastName":"Smith","birthYear":2010}
				""");
	}

	@Test
	void rejectsNamesLongerThanOneHundredCharacters() throws Exception {
		String name = "a".repeat(101);
		assertBadRequest("""
				{"firstName":"%s","lastName":"Smith","birthYear":2010,"gender":"MALE"}
				""".formatted(name));
		assertBadRequest("""
				{"firstName":"John","lastName":"%s","birthYear":2010,"gender":"MALE"}
				""".formatted(name));
	}

	@Test
	void rejectsBirthYearsOutsideTheAllowedRange() throws Exception {
		assertBadRequest("""
				{"firstName":"John","lastName":"Smith","birthYear":1899,"gender":"MALE"}
				""");
		assertBadRequest("""
				{"firstName":"John","lastName":"Smith","birthYear":"2010","gender":"MALE"}
				""");
		assertBadRequest("""
				{"firstName":"John","lastName":"Smith","birthYear":2010.5,"gender":"MALE"}
				""");
		assertBadRequest("""
				{"firstName":"John","lastName":"Smith","birthYear":%d,"gender":"MALE"}
				""".formatted(Year.now().getValue() + 1));
	}

	@Test
	void acceptsTheCurrentCalendarYear() throws Exception {
		when(athleteService.createAthlete(any(AthleteRequest.class))).thenReturn(
				new AthleteResponse(42L, "John", "Smith", Year.now().getValue(), Gender.MALE, true));

		mockMvc.perform(post("/api/athletes")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"firstName":"John","lastName":"Smith","birthYear":%d,"gender":"MALE"}
								""".formatted(Year.now().getValue())))
				.andExpect(status().isCreated());
	}

	@Test
	void rejectsInvalidGender() throws Exception {
		assertBadRequest("""
				{"firstName":"John","lastName":"Smith","birthYear":2010,"gender":"OTHER"}
				""");
	}

	@Test
	void returnsConflictForDuplicateAthlete() throws Exception {
		when(athleteService.createAthlete(any(AthleteRequest.class)))
				.thenThrow(new AthleteAlreadyExistsException());

		mockMvc.perform(post("/api/athletes")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"firstName":"John","lastName":"Smith","birthYear":2010,"gender":"MALE"}
								"""))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.title").value("Athlete already exists"));
	}

	private void assertBadRequest(String request) throws Exception {
		mockMvc.perform(post("/api/athletes")
						.contentType(MediaType.APPLICATION_JSON)
						.content(request))
				.andExpect(status().isBadRequest());
	}
}

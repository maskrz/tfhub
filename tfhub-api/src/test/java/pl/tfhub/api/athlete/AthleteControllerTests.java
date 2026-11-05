package pl.tfhub.api.athlete;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pl.tfhub.api.athlete.dto.AthleteRequest;
import pl.tfhub.api.athlete.dto.AthleteResponse;
import pl.tfhub.api.athlete.dto.AthleteSearchRequest;

import java.time.Year;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
	void emptySearchReturnsAllAthletesIncludingInactiveAsDtos() throws Exception {
		List<AthleteResponse> athletes = List.of(
				new AthleteResponse(1L, "Jan", "Kowalski", 2010, Gender.MALE, true),
				new AthleteResponse(2L, "Anna", "Nowak", 2008, Gender.FEMALE, false));
		when(athleteService.searchAthletes(new AthleteSearchRequest(null, null, null, null, null)))
				.thenReturn(athletes);

		mockMvc.perform(post("/api/athletes/search")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isOk())
				.andExpect(content().json("""
						[
						  {"id":1,"firstName":"Jan","lastName":"Kowalski","birthYear":2010,
						   "gender":"MALE","active":true},
						  {"id":2,"firstName":"Anna","lastName":"Nowak","birthYear":2008,
						   "gender":"FEMALE","active":false}
						]
						""", true));

		verify(athleteService).searchAthletes(new AthleteSearchRequest(null, null, null, null, null));
	}

	@Test
	void searchesByPartialFirstName() throws Exception {
		when(athleteService.searchAthletes(new AthleteSearchRequest("ann", null, null, null, null)))
				.thenReturn(List.of(new AthleteResponse(3L, "Anna", "Nowak", 2009, Gender.FEMALE, true)));

		mockMvc.perform(post("/api/athletes/search")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"firstName":"ann"}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].firstName").value("Anna"))
				.andExpect(jsonPath("$[0].id").value(3));

		verify(athleteService).searchAthletes(new AthleteSearchRequest("ann", null, null, null, null));
	}

	@Test
	void searchesByPartialLastNameCaseInsensitively() throws Exception {
		when(athleteService.searchAthletes(new AthleteSearchRequest(null, "KOW", null, null, null)))
				.thenReturn(List.of(new AthleteResponse(1L, "Jan", "Kowalski", 2010, Gender.MALE, true)));

		mockMvc.perform(post("/api/athletes/search")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"lastName":"KOW"}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].lastName").value("Kowalski"));

		verify(athleteService).searchAthletes(new AthleteSearchRequest(null, "KOW", null, null, null));
	}

	@Test
	void trimsTextAndIgnoresEmptyOrWhitespaceCriteria() throws Exception {
		when(athleteService.searchAthletes(new AthleteSearchRequest("Jan", null, null, null, null)))
				.thenReturn(List.of());
		when(athleteService.searchAthletes(new AthleteSearchRequest(null, null, null, null, null)))
				.thenReturn(List.of());

		mockMvc.perform(post("/api/athletes/search")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"firstName":"  Jan  ","lastName":"   "}
								"""))
				.andExpect(status().isOk())
				.andExpect(content().json("[]", true));
		mockMvc.perform(post("/api/athletes/search")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"firstName":"","lastName":"  "}
								"""))
				.andExpect(status().isOk())
				.andExpect(content().json("[]", true));

		verify(athleteService).searchAthletes(new AthleteSearchRequest("Jan", null, null, null, null));
		verify(athleteService).searchAthletes(new AthleteSearchRequest(null, null, null, null, null));
	}

	@Test
	void filtersByActiveStatusForBothValues() throws Exception {
		when(athleteService.searchAthletes(new AthleteSearchRequest(null, null, true, null, null)))
				.thenReturn(List.of(new AthleteResponse(1L, "Jan", "Kowalski", 2010, Gender.MALE, true)));
		when(athleteService.searchAthletes(new AthleteSearchRequest(null, null, false, null, null)))
				.thenReturn(List.of(new AthleteResponse(2L, "Anna", "Nowak", 2008, Gender.FEMALE, false)));

		mockMvc.perform(post("/api/athletes/search")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"active":true}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].active").value(true));
		mockMvc.perform(post("/api/athletes/search")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"active":false}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].active").value(false));
	}

	@Test
	void filtersByBothGenderValues() throws Exception {
		when(athleteService.searchAthletes(new AthleteSearchRequest(null, null, null, Gender.MALE, null)))
				.thenReturn(List.of(new AthleteResponse(1L, "Jan", "Kowalski", 2010, Gender.MALE, true)));
		when(athleteService.searchAthletes(new AthleteSearchRequest(null, null, null, Gender.FEMALE, null)))
				.thenReturn(List.of(new AthleteResponse(2L, "Anna", "Nowak", 2008, Gender.FEMALE, false)));

		mockMvc.perform(post("/api/athletes/search")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"gender":"MALE"}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].gender").value("MALE"));
		mockMvc.perform(post("/api/athletes/search")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"gender":"FEMALE"}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].gender").value("FEMALE"));
	}

	@Test
	void combinesAllSearchCriteria() throws Exception {
		AthleteSearchRequest request = new AthleteSearchRequest("Jan", "Kow", true, Gender.MALE, null);
		when(athleteService.searchAthletes(request))
				.thenReturn(List.of(new AthleteResponse(1L, "Jan", "Kowalski", 2010, Gender.MALE, true)));

		mockMvc.perform(post("/api/athletes/search")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"firstName":"Jan","lastName":"Kow","active":true,"gender":"MALE"}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].id").value(1));

		verify(athleteService).searchAthletes(request);
	}

	@Test
	void searchesByExactBirthYear() throws Exception {
		AthleteSearchRequest request = new AthleteSearchRequest(null, null, null, null, 2010);
		when(athleteService.searchAthletes(request))
				.thenReturn(List.of(new AthleteResponse(1L, "Jan", "Kowalski", 2010, Gender.MALE, true)));

		mockMvc.perform(post("/api/athletes/search")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"birthYear":2010}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].birthYear").value(2010));

		verify(athleteService).searchAthletes(request);
	}

	@Test
	void returnsNoResultsForUnmatchedBirthYear() throws Exception {
		when(athleteService.searchAthletes(new AthleteSearchRequest(null, null, null, null, 2001)))
				.thenReturn(List.of());

		mockMvc.perform(post("/api/athletes/search")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"birthYear":2001}
								"""))
				.andExpect(status().isOk())
				.andExpect(content().json("[]", true));
	}

	@Test
	void combinesBirthYearWithOtherCriteriaUsingAnd() throws Exception {
		AthleteSearchRequest byFirstName = new AthleteSearchRequest("Jan", null, null, null, 2010);
		AthleteSearchRequest byLastName = new AthleteSearchRequest(null, "Kow", null, null, 2010);
		AthleteSearchRequest byGender = new AthleteSearchRequest(null, null, null, Gender.MALE, 2010);
		AthleteSearchRequest byStatus = new AthleteSearchRequest(null, null, true, null, 2010);
		AthleteSearchRequest byMultipleCriteria =
				new AthleteSearchRequest("Jan", "Kow", true, Gender.MALE, 2010);
		AthleteResponse matchingAthlete =
				new AthleteResponse(1L, "Jan", "Kowalski", 2010, Gender.MALE, true);
		when(athleteService.searchAthletes(byFirstName)).thenReturn(List.of(matchingAthlete));
		when(athleteService.searchAthletes(byLastName)).thenReturn(List.of(matchingAthlete));
		when(athleteService.searchAthletes(byGender)).thenReturn(List.of(matchingAthlete));
		when(athleteService.searchAthletes(byStatus)).thenReturn(List.of(matchingAthlete));
		when(athleteService.searchAthletes(byMultipleCriteria)).thenReturn(List.of(matchingAthlete));

		assertSearchReturns(byFirstName, """
				{"firstName":"Jan","birthYear":2010}
				""");
		assertSearchReturns(byLastName, """
				{"lastName":"Kow","birthYear":2010}
				""");
		assertSearchReturns(byGender, """
				{"gender":"MALE","birthYear":2010}
				""");
		assertSearchReturns(byStatus, """
				{"active":true,"birthYear":2010}
				""");
		assertSearchReturns(byMultipleCriteria, """
				{"firstName":"Jan","lastName":"Kow","birthYear":2010,"gender":"MALE","active":true}
				""");
	}

	@Test
	void omittedBirthYearDoesNotRestrictSearchResults() throws Exception {
		AthleteSearchRequest request = new AthleteSearchRequest(null, null, true, Gender.FEMALE, null);
		when(athleteService.searchAthletes(request)).thenReturn(List.of(
				new AthleteResponse(1L, "Anna", "Nowak", 2008, Gender.FEMALE, true),
				new AthleteResponse(2L, "Maria", "Kowal", 2012, Gender.FEMALE, true)));

		mockMvc.perform(post("/api/athletes/search")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"gender":"FEMALE","active":true}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[0].birthYear").value(2008))
				.andExpect(jsonPath("$[1].birthYear").value(2012));

		verify(athleteService).searchAthletes(request);
	}

	@Test
	void rejectsInvalidBirthYearValues() throws Exception {
		assertSearchBadRequest("""
				{"birthYear":1899}
				""");
		assertSearchBadRequest("""
				{"birthYear":%d}
				""".formatted(Year.now().getValue() + 1));
		assertSearchBadRequest("""
				{"birthYear":"2010"}
				""");
		assertSearchBadRequest("""
				{"birthYear":2010.5}
				""");
	}

	@Test
	void returnsEmptyArrayWhenSearchHasNoMatches() throws Exception {
		when(athleteService.searchAthletes(new AthleteSearchRequest("missing", null, null, null, null)))
				.thenReturn(List.of());

		mockMvc.perform(post("/api/athletes/search")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"firstName":"missing"}
								"""))
				.andExpect(status().isOk())
				.andExpect(content().json("[]", true));
	}

	@Test
	void rejectsInvalidGenderAndMalformedSearchJson() throws Exception {
		mockMvc.perform(post("/api/athletes/search")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"gender":"OTHER"}
								"""))
				.andExpect(status().isBadRequest());

		mockMvc.perform(post("/api/athletes/search")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"firstName\":"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void retrievesExistingActiveAthleteAsResponseDto() throws Exception {
		when(athleteService.getAthlete(42L)).thenReturn(
				new AthleteResponse(42L, "Jan", "Kowalski", 2010, Gender.MALE, true));

		mockMvc.perform(get("/api/athletes/42"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(42))
				.andExpect(jsonPath("$.firstName").value("Jan"))
				.andExpect(jsonPath("$.lastName").value("Kowalski"))
				.andExpect(jsonPath("$.birthYear").value(2010))
				.andExpect(jsonPath("$.gender").value("MALE"))
				.andExpect(jsonPath("$.active").value(true))
				.andExpect(content().json("""
						{"id":42,"firstName":"Jan","lastName":"Kowalski","birthYear":2010,
						"gender":"MALE","active":true}
						""", true));

		verify(athleteService).getAthlete(42L);
	}

	@Test
	void retrievesExistingInactiveAthlete() throws Exception {
		when(athleteService.getAthlete(7L)).thenReturn(
				new AthleteResponse(7L, "Anna", "Nowak", 2008, Gender.FEMALE, false));

		mockMvc.perform(get("/api/athletes/7"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(7))
				.andExpect(jsonPath("$.firstName").value("Anna"))
				.andExpect(jsonPath("$.lastName").value("Nowak"))
				.andExpect(jsonPath("$.birthYear").value(2008))
				.andExpect(jsonPath("$.gender").value("FEMALE"))
				.andExpect(jsonPath("$.active").value(false));
	}

	@Test
	void returnsNotFoundWhenAthleteDoesNotExist() throws Exception {
		when(athleteService.getAthlete(99L)).thenThrow(new AthleteNotFoundException(99L));

		mockMvc.perform(get("/api/athletes/99"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.title").value("Athlete not found"));
	}

	@Test
	void rejectsInvalidAthleteIds() throws Exception {
		assertInvalidAthleteId("0");
		assertInvalidAthleteId("-1");
		assertInvalidAthleteId("not-a-number");
		assertInvalidAthleteId("9223372036854775808");
	}

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

	private void assertInvalidAthleteId(String id) throws Exception {
		mockMvc.perform(get("/api/athletes/{id}", id))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.title").value("Invalid athlete ID"));
	}

	private void assertSearchReturns(AthleteSearchRequest request, String json) throws Exception {
		mockMvc.perform(post("/api/athletes/search")
						.contentType(MediaType.APPLICATION_JSON)
						.content(json))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].birthYear").value(2010));
		verify(athleteService).searchAthletes(request);
	}

	private void assertSearchBadRequest(String json) throws Exception {
		mockMvc.perform(post("/api/athletes/search")
						.contentType(MediaType.APPLICATION_JSON)
						.content(json))
				.andExpect(status().isBadRequest());
	}
}

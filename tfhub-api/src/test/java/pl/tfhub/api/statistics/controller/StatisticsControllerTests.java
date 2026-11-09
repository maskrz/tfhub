package pl.tfhub.api.statistics.controller;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pl.tfhub.api.statistics.dto.response.ClubRecordsResponse;
import pl.tfhub.api.statistics.dto.response.DashboardOverviewResponse;
import pl.tfhub.api.statistics.exception.InvalidStatisticsParameterException;
import pl.tfhub.api.statistics.exception.StatisticsTestNotFoundException;
import pl.tfhub.api.statistics.service.StatisticsService;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StatisticsController.class)
class StatisticsControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private StatisticsService statisticsService;

	@Test
	void returnsDashboardOverviewDto() throws Exception {
		when(statisticsService.getDashboardOverview(null))
				.thenReturn(new DashboardOverviewResponse(2, List.of(), List.of(2026), List.of()));

		mockMvc.perform(get("/api/statistics/dashboard"))
				.andExpect(status().isOk())
				.andExpect(content().json("""
						{"activeAthleteCount":2,"availableTests":[],"availableSeasons":[2026],"recentResults":[]}
						""", true))
				.andExpect(jsonPath("$.class").doesNotExist());

		verify(statisticsService).getDashboardOverview(null);
	}

	@Test
	void delegatesClubRecordParametersAndReturnsResponseDto() throws Exception {
		when(statisticsService.getClubRecords("10", "2026", null))
				.thenReturn(new ClubRecordsResponse(10L, 2026, List.of(), List.of()));

		mockMvc.perform(get("/api/statistics/records")
						.param("testId", "10")
						.param("season", "2026"))
				.andExpect(status().isOk())
				.andExpect(content().json("""
						{"testId":10,"season":2026,"FEMALE":[],"MALE":[]}
						""", true))
				.andExpect(jsonPath("$.class").doesNotExist());

		verify(statisticsService).getClubRecords("10", "2026", null);
	}

	@Test
	void mapsUnknownTestAndInvalidParametersToStandardProblemResponses() throws Exception {
		when(statisticsService.getClubRecords("404", null, null))
				.thenThrow(new StatisticsTestNotFoundException(404));
		when(statisticsService.getClubRecords("0", null, null))
				.thenThrow(new InvalidStatisticsParameterException());
		when(statisticsService.getClubRecords(null, null, null))
				.thenThrow(new InvalidStatisticsParameterException());

		mockMvc.perform(get("/api/statistics/records").param("testId", "404"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.title").value("Test not found"));
		mockMvc.perform(get("/api/statistics/records").param("testId", "0"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.title").value("Invalid statistics parameter"));
		mockMvc.perform(get("/api/statistics/records"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.title").value("Invalid statistics parameter"));
	}
}

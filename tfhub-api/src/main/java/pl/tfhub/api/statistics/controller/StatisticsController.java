package pl.tfhub.api.statistics.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pl.tfhub.api.statistics.dto.response.ClubRecordsResponse;
import pl.tfhub.api.statistics.dto.response.DashboardOverviewResponse;
import pl.tfhub.api.statistics.service.StatisticsService;

@RestController
@RequestMapping("/api/statistics")
public class StatisticsController {

	private final StatisticsService statisticsService;

	public StatisticsController(StatisticsService statisticsService) {
		this.statisticsService = statisticsService;
	}

	@GetMapping("/dashboard")
	public DashboardOverviewResponse getDashboardOverview() {
		return statisticsService.getDashboardOverview();
	}

	@GetMapping("/records")
	public ClubRecordsResponse getClubRecords(
			@RequestParam(required = false) String testId,
			@RequestParam(required = false) String season) {
		return statisticsService.getClubRecords(testId, season);
	}
}

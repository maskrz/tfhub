package pl.tfhub.api.statistics.service;

import java.time.DateTimeException;
import java.time.Year;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.tfhub.api.athlete.enums.Gender;
import pl.tfhub.api.statistics.dto.response.ClubRecordResponse;
import pl.tfhub.api.statistics.dto.response.ClubRecordsResponse;
import pl.tfhub.api.statistics.dto.response.DashboardOverviewResponse;
import pl.tfhub.api.statistics.exception.InvalidStatisticsParameterException;
import pl.tfhub.api.statistics.exception.StatisticsTestNotFoundException;
import pl.tfhub.api.statistics.repository.ClubRecordRow;
import pl.tfhub.api.statistics.repository.StatisticsRepository;

@Service
public class StatisticsService {

	private final StatisticsRepository statisticsRepository;

	public StatisticsService(StatisticsRepository statisticsRepository) {
		this.statisticsRepository = statisticsRepository;
	}

	@Transactional(readOnly = true)
	public DashboardOverviewResponse getDashboardOverview() {
		return new DashboardOverviewResponse(
				statisticsRepository.countActiveAthletes(),
				statisticsRepository.findAvailableTests(),
				statisticsRepository.findAvailableSeasons(),
				statisticsRepository.findRecentResults());
	}

	@Transactional(readOnly = true)
	public ClubRecordsResponse getClubRecords(String testIdValue, String seasonValue) {
		long testId = parseTestId(testIdValue);
		Integer season = parseSeason(seasonValue);
		if (!statisticsRepository.existsTest(testId)) {
			throw new StatisticsTestNotFoundException(testId);
		}

		return new ClubRecordsResponse(
				testId,
				season,
				statisticsRepository.findClubRecords(testId, season, Gender.FEMALE).stream()
						.map(this::toRecord)
						.toList(),
				statisticsRepository.findClubRecords(testId, season, Gender.MALE).stream()
						.map(this::toRecord)
						.toList());
	}

	private ClubRecordResponse toRecord(ClubRecordRow row) {
		return new ClubRecordResponse(
				row.place(),
				row.athleteId(),
				row.athleteName(),
				row.value(),
				row.unit(),
				row.resultDate());
	}

	private long parseTestId(String value) {
		try {
			long testId = Long.parseLong(value);
			if (testId > 0) {
				return testId;
			}
		} catch (NumberFormatException | NullPointerException exception) {
			throw new InvalidStatisticsParameterException();
		}
		throw new InvalidStatisticsParameterException();
	}

	private Integer parseSeason(String value) {
		if (value == null) {
			return null;
		}
		try {
			int season = Integer.parseInt(value);
			if (season < 1) {
				throw new InvalidStatisticsParameterException();
			}
			Year.of(season);
			return season;
		} catch (NumberFormatException | DateTimeException exception) {
			throw new InvalidStatisticsParameterException();
		}
	}
}

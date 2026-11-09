package pl.tfhub.api.statistics.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.tfhub.api.statistics.exception.InvalidStatisticsParameterException;
import pl.tfhub.api.statistics.exception.StatisticsTestNotFoundException;
import pl.tfhub.api.statistics.repository.StatisticsRepository;
import pl.tfhub.api.common.authorization.AccessScopeService;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StatisticsServiceTests {

	@Mock
	private StatisticsRepository statisticsRepository;

	@Mock
	private AccessScopeService accessScopeService;

	@InjectMocks
	private StatisticsService statisticsService;

	@Test
	void rejectsMissingNonPositiveAndMalformedTestIds() {
		assertThrows(InvalidStatisticsParameterException.class,
				() -> statisticsService.getClubRecords(null, null));
		assertThrows(InvalidStatisticsParameterException.class,
				() -> statisticsService.getClubRecords("0", null));
		assertThrows(InvalidStatisticsParameterException.class,
				() -> statisticsService.getClubRecords("not-an-id", null));
		verify(statisticsRepository, never()).existsTest(org.mockito.ArgumentMatchers.anyLong());
	}

	@Test
	void rejectsNonCalendarYearValues() {
		assertThrows(InvalidStatisticsParameterException.class,
				() -> statisticsService.getClubRecords("12", "0"));
		assertThrows(InvalidStatisticsParameterException.class,
				() -> statisticsService.getClubRecords("12", "-1"));
		assertThrows(InvalidStatisticsParameterException.class,
				() -> statisticsService.getClubRecords("12", "2026.5"));
		assertThrows(InvalidStatisticsParameterException.class,
				() -> statisticsService.getClubRecords("12", "not-a-year"));
		verify(statisticsRepository, never()).existsTest(org.mockito.ArgumentMatchers.anyLong());
	}

	@Test
	void returnsNotFoundForUnknownTest() {
		when(statisticsRepository.existsTest(404L)).thenReturn(false);

		assertThrows(StatisticsTestNotFoundException.class,
				() -> statisticsService.getClubRecords("404", null));

		verify(statisticsRepository).existsTest(404L);
		verify(statisticsRepository, never()).findClubRecords(
				org.mockito.ArgumentMatchers.eq(404L),
				org.mockito.ArgumentMatchers.isNull(),
				org.mockito.ArgumentMatchers.any());
	}
}

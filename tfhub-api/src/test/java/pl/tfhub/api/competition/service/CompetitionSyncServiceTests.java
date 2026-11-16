package pl.tfhub.api.competition.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;
import pl.tfhub.api.athlete.dto.response.AthleteResponse;
import pl.tfhub.api.athlete.enums.Gender;
import pl.tfhub.api.athlete.service.AthleteService;
import pl.tfhub.api.common.authorization.AccessScopeService;
import pl.tfhub.api.competition.dto.response.CompetitionSyncStatusResponse;
import pl.tfhub.api.competition.entity.CompetitionSyncRun;
import pl.tfhub.api.competition.enums.CompetitionSeasonType;
import pl.tfhub.api.competition.enums.CompetitionSyncMode;
import pl.tfhub.api.competition.enums.CompetitionSyncStatus;
import pl.tfhub.api.competition.pzla.PzlaPageParser;
import pl.tfhub.api.competition.pzla.PzlaPageSource;
import pl.tfhub.api.competition.pzla.PzlaSynchronizationException;

@ExtendWith(MockitoExtension.class)
class CompetitionSyncServiceTests {

	@Mock
	private AthleteService athleteService;
	@Mock
	private AccessScopeService accessScopeService;
	@Mock
	private CompetitionSyncStateService stateService;
	@Mock
	private PzlaPageSource pageSource;
	@Mock
	private ThreadPoolTaskExecutor taskExecutor;

	private final PzlaPageParser pageParser = new PzlaPageParser();
	private CompetitionSyncService service;
	private CompetitionSyncRun run;

	@BeforeEach
	void setUp() {
		service = new CompetitionSyncService(
				athleteService, accessScopeService, stateService, pageSource, pageParser, taskExecutor);
	}

	private void configureSuccessfulSync() {
		run = new CompetitionSyncRun(8L, CompetitionSyncMode.CURRENT_SEASON);
		ReflectionTestUtils.setField(run, "id", 72L);
		when(stateService.createRun(anyLong(), any(CompetitionSyncMode.class))).thenAnswer(invocation -> {
			CompetitionSyncMode mode = invocation.getArgument(1);
			run = new CompetitionSyncRun(8L, mode);
			ReflectionTestUtils.setField(run, "id", 72L);
			return run;
		});
		when(stateService.markRunning(72L)).thenAnswer(invocation -> {
			run.start(Instant.now());
			return run;
		});
		when(stateService.lastSuccessfulRun(anyLong())).thenReturn(Optional.empty());
		when(athleteService.getAthlete(8L)).thenReturn(new AthleteResponse(
				8L, "Jan", "Kowalski", 2010, Gender.MALE, true, null, List.of(), "163067"));
		doAnswer(invocation -> {
			((Runnable) invocation.getArgument(0)).run();
			return null;
		}).when(taskExecutor).execute(any(Runnable.class));
	}

	@Test
	void currentSeasonModeFetchesOnlyTheCurrentPageAndImportsItsResults() {
		configureSuccessfulSync();
		when(pageSource.fetchCurrentSeason("163067")).thenReturn(html());

		CompetitionSyncStatusResponse response = service.startSync(8L, CompetitionSyncMode.CURRENT_SEASON);

		assertThat(response.status()).isEqualTo(CompetitionSyncStatus.RUNNING);
		verify(pageSource).fetchCurrentSeason("163067");
		verify(pageSource, never()).fetchSeason(any(), any());
		verify(stateService).complete(anyLong(), any());
	}

	@Test
	void fullHistoryModeUsesOnlySeasonLinksFoundOnTheCurrentPage() {
		configureSuccessfulSync();
		when(pageSource.fetchCurrentSeason("163067")).thenReturn(html());
		when(pageSource.fetchSeason(any(), any())).thenReturn(html());

		service.startSync(8L, CompetitionSyncMode.FULL_HISTORY);

		verify(pageSource).fetchCurrentSeason("163067");
		verify(pageSource).fetchSeason("163067", new pl.tfhub.api.competition.pzla.PzlaSeason(
				2026, CompetitionSeasonType.INDOOR));
		verify(pageSource).fetchSeason("163067", new pl.tfhub.api.competition.pzla.PzlaSeason(
				2025, CompetitionSeasonType.OUTDOOR));
		verify(pageSource).fetchSeason("163067", new pl.tfhub.api.competition.pzla.PzlaSeason(
				2025, CompetitionSeasonType.INDOOR));
		verify(stateService).complete(anyLong(), any());
	}

	@Test
	void failedHistoryRetrievalRecordsFailureWithoutApplyingPartialResults() {
		configureSuccessfulSync();
		when(pageSource.fetchCurrentSeason("163067")).thenReturn(html());
		when(pageSource.fetchSeason(any(), any())).thenThrow(new PzlaSynchronizationException(
				"PZLA_CONNECTION_FAILED", "Could not retrieve results from PZLA"));

		service.startSync(8L, CompetitionSyncMode.FULL_HISTORY);

		verify(stateService, never()).complete(anyLong(), any());
		verify(stateService).fail(72L, "PZLA_CONNECTION_FAILED", "Could not retrieve results from PZLA");
	}

	@Test
	void synchronizationRequiresAdministratorAuthorization() {
		doThrow(new AccessDeniedException("Forbidden")).when(accessScopeService).requireAdmin();

		assertThatThrownBy(() -> service.startSync(8L, CompetitionSyncMode.CURRENT_SEASON))
				.isInstanceOf(AccessDeniedException.class);

		verify(athleteService, never()).getAthlete(8L);
	}

	@Test
	void unexpectedFailureLogsStageAndLocationWithoutSensitiveExceptionDetails() {
		configureSuccessfulSync();
		when(pageSource.fetchCurrentSeason("163067"))
				.thenThrow(new IllegalStateException("private athlete data 163067"));
		Logger logger = (Logger) LoggerFactory.getLogger(CompetitionSyncService.class);
		ListAppender<ILoggingEvent> appender = new ListAppender<>();
		appender.start();
		logger.addAppender(appender);
		try {
			service.startSync(8L, CompetitionSyncMode.CURRENT_SEASON);

			verify(stateService).fail(72L, "SYNC_FAILED", "Synchronizacja wyników PZLA nie powiodła się");
			String messages = appender.list.stream()
					.map(ILoggingEvent::getFormattedMessage)
					.reduce("", (left, right) -> left + "\n" + right);
			assertThat(messages).contains("run 72 started", "fetching current season",
					"java.lang.IllegalStateException", "CompetitionSyncService");
			assertThat(messages).doesNotContain("private athlete data", "163067", "Kowalski");
		} finally {
			logger.detachAppender(appender);
			appender.stop();
		}
	}

	private String html() {
		return """
				<html lang="pl"><body>
				<table><tr><td>ID zawodnika<br>163067</td><td>Sezon: 2026</td></tr></table>
				<p>Wyniki sezonu</p>
				<a href="personal.php?page=sb&amp;nr_zaw=163067&amp;r=2&amp;sezon=2026&amp;sezon2=Z">2026</a>
				<a href="personal.php?page=sb&amp;nr_zaw=163067&amp;r=2&amp;sezon=2025&amp;sezon2=L">2025</a>
				<a href="personal.php?page=sb&amp;nr_zaw=163067&amp;r=2&amp;sezon=2025&amp;sezon2=Z">2025</a>
				<table>
				<tr><td title="40159">100 m</td><td title="Miejsce: 1 Seria: 3 s"><b>14.18</b> (-1.4)</td>
				<td></td><td>2025-05-24</td><td title="Mistrzostwa"><a href="mityng.php?Nr[]=10312">Karpacz</a></td><td></td></tr>
				</table></body></html>
				""";
	}
}

package pl.tfhub.api.competition.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.RejectedExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.concurrent.DelegatingSecurityContextRunnable;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import pl.tfhub.api.athlete.dto.response.AthleteResponse;
import pl.tfhub.api.athlete.service.AthleteService;
import pl.tfhub.api.common.authorization.AccessScopeService;
import pl.tfhub.api.competition.dto.response.CompetitionSyncStatusResponse;
import pl.tfhub.api.competition.dto.response.GroupCompetitionSyncResponse;
import pl.tfhub.api.competition.entity.CompetitionSyncRun;
import pl.tfhub.api.competition.enums.CompetitionSeasonType;
import pl.tfhub.api.competition.enums.CompetitionSyncMode;
import pl.tfhub.api.competition.enums.CompetitionSyncStatus;
import pl.tfhub.api.competition.exception.CompetitionSyncAlreadyRunningException;
import pl.tfhub.api.competition.exception.PzlaIdRequiredException;
import pl.tfhub.api.competition.pzla.PzlaPageParser;
import pl.tfhub.api.competition.pzla.PzlaPageSource;
import pl.tfhub.api.competition.pzla.PzlaParsedPage;
import pl.tfhub.api.competition.pzla.PzlaResult;
import pl.tfhub.api.competition.pzla.PzlaSeason;
import pl.tfhub.api.competition.pzla.PzlaSynchronizationException;

@Service
public class CompetitionSyncService {

	private static final Logger LOGGER = LoggerFactory.getLogger(CompetitionSyncService.class);

	private final AthleteService athleteService;
	private final AccessScopeService accessScopeService;
	private final CompetitionSyncStateService stateService;
	private final PzlaPageSource pageSource;
	private final PzlaPageParser pageParser;
	private final ThreadPoolTaskExecutor taskExecutor;

	public CompetitionSyncService(
			AthleteService athleteService,
			AccessScopeService accessScopeService,
			CompetitionSyncStateService stateService,
			PzlaPageSource pageSource,
			PzlaPageParser pageParser,
			@Qualifier("competitionSyncTaskExecutor") ThreadPoolTaskExecutor taskExecutor) {
		this.athleteService = athleteService;
		this.accessScopeService = accessScopeService;
		this.stateService = stateService;
		this.pageSource = pageSource;
		this.pageParser = pageParser;
		this.taskExecutor = taskExecutor;
	}

	public CompetitionSyncStatusResponse startSync(long athleteId, CompetitionSyncMode mode) {
		accessScopeService.requireAdmin();
		AthleteResponse athlete = athleteService.getAthlete(athleteId);
		if (athlete.pzlaId() == null) {
			throw new PzlaIdRequiredException();
		}
		CompetitionSyncRun run = stateService.createRun(athleteId, mode);
		dispatch(run.getId());
		return statusFor(run);
	}

	public GroupCompetitionSyncResponse startGroupSync(long groupId, CompetitionSyncMode mode) {
		accessScopeService.requireAdmin();
		List<CompetitionSyncStatusResponse> submitted = new ArrayList<>();
		List<GroupCompetitionSyncResponse.SkippedAthlete> skipped = new ArrayList<>();
		for (AthleteService.PzlaSyncTarget target : athleteService.getPzlaSyncTargetsForGroup(groupId)) {
			if (target.pzlaId() == null) {
				skipped.add(new GroupCompetitionSyncResponse.SkippedAthlete(
						target.athleteId(), "Brak skonfigurowanego identyfikatora PZLA"));
				continue;
			}
			try {
				CompetitionSyncRun run = stateService.createRun(target.athleteId(), mode);
				dispatch(run.getId());
				submitted.add(statusFor(run));
			} catch (CompetitionSyncAlreadyRunningException exception) {
				skipped.add(new GroupCompetitionSyncResponse.SkippedAthlete(
						target.athleteId(), "Synchronizacja tego zawodnika już trwa"));
			}
		}
		return new GroupCompetitionSyncResponse(groupId, submitted, skipped);
	}

	public CompetitionSyncStatusResponse getLatestStatus(long athleteId) {
		athleteService.getAthlete(athleteId);
		Optional<CompetitionSyncRun> latest = stateService.latestRun(athleteId);
		Instant lastSuccess = stateService.lastSuccessfulRun(athleteId)
				.map(CompetitionSyncRun::getFinishedAt)
				.orElse(null);
		if (latest.isEmpty()) {
			return new CompetitionSyncStatusResponse(
					null, athleteId, null, CompetitionSyncStatus.NOT_SYNCHRONIZED,
					null, null, null, lastSuccess, null, null);
		}
		return CompetitionSyncStatusResponse.from(latest.get(), lastSuccess);
	}

	public CompetitionSyncStatusResponse getRunStatus(long runId) {
		CompetitionSyncRun run = stateService.getRun(runId);
		athleteService.getAthlete(run.getAthleteId());
		Instant lastSuccess = stateService.lastSuccessfulRun(run.getAthleteId())
				.map(CompetitionSyncRun::getFinishedAt)
				.orElse(null);
		return CompetitionSyncStatusResponse.from(run, lastSuccess);
	}

	@EventListener(ApplicationReadyEvent.class)
	public void failInterruptedRuns() {
		int failedRuns = stateService.failInterruptedRuns();
		if (failedRuns > 0) {
			LOGGER.warn("Marked {} incomplete competition synchronization runs as failed after restart", failedRuns);
		}
	}

	private void dispatch(long runId) {
		SecurityContext context = SecurityContextHolder.createEmptyContext();
		context.setAuthentication(SecurityContextHolder.getContext().getAuthentication());
		try {
			taskExecutor.execute(new DelegatingSecurityContextRunnable(() -> process(runId), context));
			LOGGER.info("Competition synchronization run {} queued", runId);
		} catch (RejectedExecutionException exception) {
			stateService.fail(
					runId,
					"WORKER_QUEUE_FULL",
					"Synchronizacja nie może zostać teraz uruchomiona");
			LOGGER.warn("Competition synchronization run {} rejected: worker queue is full", runId);
		}
	}

	private void process(long runId) {
		CompetitionSyncRun run;
		String stage = "starting";
		long startedAt = System.nanoTime();
		try {
			run = stateService.markRunning(runId);
			if (run.getStatus() != CompetitionSyncStatus.RUNNING) {
				LOGGER.warn("Competition synchronization run {} not started: status {}", runId, run.getStatus());
				return;
			}
			LOGGER.info("Competition synchronization run {} started in {} mode", runId, run.getMode());
			stage = "loading athlete";
			AthleteResponse athlete = athleteService.getAthlete(run.getAthleteId());
			if (athlete.pzlaId() == null) {
				throw new PzlaIdRequiredException();
			}
			stage = "fetching current season";
			PzlaParsedPage currentPage = pageParser.parse(
					pageSource.fetchCurrentSeason(athlete.pzlaId()),
					athlete.pzlaId(),
					null,
					null);
			Map<String, PzlaResult> imported = new LinkedHashMap<>();
			mergePage(imported, currentPage.results(), false);
			LOGGER.info("Competition synchronization run {} parsed current season: {} results, {} historical seasons",
					runId, currentPage.results().size(), currentPage.availableSeasons().size());
			if (run.getMode() == CompetitionSyncMode.FULL_HISTORY) {
				for (PzlaSeason season : currentPage.availableSeasons()) {
					stage = "fetching season " + season.year() + " " + season.type();
					LOGGER.info("Competition synchronization run {} fetching season {} {}",
							runId, season.year(), season.type());
					PzlaParsedPage seasonPage = pageParser.parse(
							pageSource.fetchSeason(athlete.pzlaId(), season),
							athlete.pzlaId(),
							season.year(),
							season.type());
					mergePage(imported, seasonPage.results(), true);
					LOGGER.info("Competition synchronization run {} parsed season {} {}: {} results",
							runId, season.year(), season.type(), seasonPage.results().size());
				}
			}
			stage = "saving results";
			stateService.complete(runId, List.copyOf(imported.values()));
			LOGGER.info("Competition synchronization run {} completed: {} results in {} ms",
					runId, imported.size(), (System.nanoTime() - startedAt) / 1_000_000);
		} catch (PzlaSynchronizationException exception) {
			stateService.fail(runId, exception.getCode(), exception.getMessage());
			LOGGER.warn("Competition synchronization run {} failed during {}: {}",
					runId, stage, exception.getCode());
		} catch (PzlaIdRequiredException exception) {
			stateService.fail(runId, "PZLA_ID_REQUIRED", exception.getMessage());
			LOGGER.warn("Competition synchronization run {} failed during {}: PZLA_ID_REQUIRED", runId, stage);
		} catch (RuntimeException exception) {
			stateService.fail(runId, "SYNC_FAILED", "Synchronizacja wyników PZLA nie powiodła się");
			LOGGER.error("Competition synchronization run {} failed during {}: {} at {}",
					runId, stage, exception.getClass().getName(),
					Arrays.stream(exception.getStackTrace()).limit(8).toList());
		}
	}

	private void mergePage(
			Map<String, PzlaResult> imported,
			List<PzlaResult> pageResults,
			boolean seasonPage) {
		Set<String> pageKeys = new LinkedHashSet<>();
		for (PzlaResult result : pageResults) {
			if (!pageKeys.add(result.sourceFingerprint())) {
				throw new PzlaSynchronizationException(
						"PZLA_DUPLICATE_AMBIGUOUS", "PZLA returned ambiguous duplicate result rows");
			}
			PzlaResult previous = imported.get(result.sourceFingerprint());
			if (previous == null || seasonPage) {
				imported.put(result.sourceFingerprint(), result);
			} else if (previous.originalMark().equals(result.originalMark())
					&& previous.seasonType() == CompetitionSeasonType.UNKNOWN
					&& result.seasonType() != CompetitionSeasonType.UNKNOWN) {
				imported.put(result.sourceFingerprint(), result);
			} else if (!previous.originalMark().equals(result.originalMark())) {
				throw new PzlaSynchronizationException(
						"PZLA_CORRECTION_AMBIGUOUS", "PZLA returned conflicting versions of the same result");
			}
		}
	}

	private CompetitionSyncStatusResponse statusFor(CompetitionSyncRun run) {
		Instant lastSuccess = stateService.lastSuccessfulRun(run.getAthleteId())
				.map(CompetitionSyncRun::getFinishedAt)
				.orElse(null);
		return CompetitionSyncStatusResponse.from(run, lastSuccess);
	}
}

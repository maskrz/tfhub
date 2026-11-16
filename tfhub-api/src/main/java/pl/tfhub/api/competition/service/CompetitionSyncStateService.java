package pl.tfhub.api.competition.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.tfhub.api.competition.entity.CompetitionEvent;
import pl.tfhub.api.competition.entity.CompetitionResult;
import pl.tfhub.api.competition.entity.CompetitionSyncRun;
import pl.tfhub.api.competition.enums.CompetitionResultSource;
import pl.tfhub.api.competition.enums.CompetitionSyncMode;
import pl.tfhub.api.competition.enums.CompetitionSyncStatus;
import pl.tfhub.api.competition.exception.CompetitionSyncAlreadyRunningException;
import pl.tfhub.api.competition.exception.CompetitionSyncNotFoundException;
import pl.tfhub.api.competition.exception.CompetitionEventNotFoundException;
import pl.tfhub.api.competition.pzla.PzlaResult;
import pl.tfhub.api.competition.repository.CompetitionEventRepository;
import pl.tfhub.api.competition.repository.CompetitionResultRepository;
import pl.tfhub.api.competition.repository.CompetitionSyncRunRepository;

@Service
public class CompetitionSyncStateService {

	private static final String ACTIVE_RUN_CONSTRAINT = "uq_competition_sync_runs_active_athlete";

	private final CompetitionSyncRunRepository runRepository;
	private final CompetitionEventRepository eventRepository;
	private final CompetitionResultRepository resultRepository;

	public CompetitionSyncStateService(
			CompetitionSyncRunRepository runRepository,
			CompetitionEventRepository eventRepository,
			CompetitionResultRepository resultRepository) {
		this.runRepository = runRepository;
		this.eventRepository = eventRepository;
		this.resultRepository = resultRepository;
	}

	@Transactional
	public CompetitionSyncRun createRun(long athleteId, CompetitionSyncMode mode) {
		try {
			return runRepository.saveAndFlush(new CompetitionSyncRun(athleteId, mode));
		} catch (DataIntegrityViolationException exception) {
			if (hasConstraint(exception, ACTIVE_RUN_CONSTRAINT)) {
				throw new CompetitionSyncAlreadyRunningException();
			}
			throw exception;
		}
	}

	@Transactional
	public CompetitionSyncRun markRunning(long runId) {
		CompetitionSyncRun run = findRun(runId);
		if (run.getStatus() != CompetitionSyncStatus.QUEUED) {
			return run;
		}
		run.start(Instant.now());
		return runRepository.saveAndFlush(run);
	}

	@Transactional
	public CompetitionSyncRun complete(long runId, List<PzlaResult> importedResults) {
		CompetitionSyncRun run = findRun(runId);
		for (PzlaResult imported : importedResults) {
			upsert(run.getAthleteId(), imported);
		}
		run.succeed(Instant.now());
		return runRepository.saveAndFlush(run);
	}

	@Transactional
	public void fail(long runId, String code, String safeMessage) {
		CompetitionSyncRun run = runRepository.findById(runId).orElse(null);
		if (run == null || run.getStatus() == CompetitionSyncStatus.SUCCEEDED
				|| run.getStatus() == CompetitionSyncStatus.FAILED) {
			return;
		}
		run.fail(Instant.now(), limit(code, 60), limit(safeMessage, 500));
		runRepository.saveAndFlush(run);
	}

	@Transactional(readOnly = true)
	public CompetitionSyncRun getRun(long id) {
		return findRun(id);
	}

	@Transactional(readOnly = true)
	public Optional<CompetitionSyncRun> latestRun(long athleteId) {
		return runRepository.findFirstByAthleteIdOrderByRequestedAtDescIdDesc(athleteId);
	}

	@Transactional(readOnly = true)
	public Optional<CompetitionSyncRun> lastSuccessfulRun(long athleteId) {
		return runRepository.findFirstByAthleteIdAndStatusOrderByFinishedAtDescIdDesc(
				athleteId, CompetitionSyncStatus.SUCCEEDED);
	}

	@Transactional
	public int failInterruptedRuns() {
		List<CompetitionSyncRun> queued = runRepository.findByStatusOrderByRequestedAtAsc(
				CompetitionSyncStatus.QUEUED);
		List<CompetitionSyncRun> running = runRepository.findByStatusOrderByRequestedAtAsc(
				CompetitionSyncStatus.RUNNING);
		queued.forEach(run -> run.fail(
				Instant.now(), "WORKER_RESTARTED", "Synchronization did not complete before application restart"));
		running.forEach(run -> run.fail(
				Instant.now(), "WORKER_RESTARTED", "Synchronization did not complete before application restart"));
		runRepository.saveAllAndFlush(queued);
		runRepository.saveAllAndFlush(running);
		return queued.size() + running.size();
	}

	private void upsert(long athleteId, PzlaResult imported) {
		CompetitionEvent event = eventRepository.findByNormalizedName(imported.eventName())
				.orElseGet(() -> eventRepository.saveAndFlush(
						new CompetitionEvent(imported.eventName(), imported.unit())));
		if (!event.getUnit().equals(imported.unit())) {
			throw new pl.tfhub.api.competition.pzla.PzlaSynchronizationException(
					"PZLA_EVENT_UNIT_MISMATCH",
					"An imported discipline has a different unit from the event catalog");
		}
		CompetitionResult result = resultRepository.findByAthleteIdAndSourceFingerprint(
						athleteId, imported.sourceFingerprint())
				.orElse(null);
		if (result == null && imported.sourceRecordKey() != null) {
			result = resultRepository.findByAthleteIdAndSourceRecordKey(
					athleteId, imported.sourceRecordKey()).orElse(null);
		}
		if (result == null) {
			result = new CompetitionResult(
					athleteId,
					event,
					imported.resultDate(),
					imported.originalMark(),
					imported.numericValue(),
					event.getUnit(),
					imported.wind(),
					imported.season(),
					imported.seasonType(),
					imported.competitionName(),
					imported.location(),
					imported.resultRound(),
					CompetitionResultSource.PZLA,
					imported.sourceRecordKey(),
					imported.sourceFingerprint());
		} else {
			result.update(
					event,
					imported.resultDate(),
					imported.originalMark(),
					imported.numericValue(),
					event.getUnit(),
					imported.wind(),
					imported.season(),
					imported.seasonType(),
					imported.competitionName(),
					imported.location(),
					imported.resultRound(),
					CompetitionResultSource.PZLA,
					imported.sourceRecordKey(),
					imported.sourceFingerprint());
		}
		resultRepository.save(result);
	}

	private CompetitionSyncRun findRun(long id) {
		return runRepository.findById(id).orElseThrow(() -> new CompetitionSyncNotFoundException(id));
	}

	private boolean hasConstraint(Throwable exception, String constraintName) {
		Throwable cause = exception;
		while (cause != null) {
			if (cause instanceof ConstraintViolationException violation
					&& constraintName.equals(violation.getConstraintName())) {
				return true;
			}
			cause = cause.getCause();
		}
		return false;
	}

	private String limit(String value, int maxLength) {
		if (value == null || value.length() <= maxLength) {
			return value;
		}
		return value.substring(0, maxLength);
	}
}

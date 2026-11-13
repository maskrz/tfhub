package pl.tfhub.api.trainingimport.service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.ThreadPoolExecutor;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;
import pl.tfhub.api.athlete.repository.AthleteRepository;
import pl.tfhub.api.common.authorization.AccessScopeService;
import pl.tfhub.api.traininggroup.entity.TrainingGroup;
import pl.tfhub.api.trainingimport.entity.TrainingImport;
import pl.tfhub.api.trainingimport.entity.TrainingImportAttempt;
import pl.tfhub.api.trainingimport.enums.TrainingImportAttemptStatus;
import pl.tfhub.api.trainingimport.enums.TrainingImportStatus;
import pl.tfhub.api.trainingimport.repository.TrainingImportAttemptRepository;
import pl.tfhub.api.trainingimport.repository.TrainingImportDraftAthleteRowRepository;
import pl.tfhub.api.trainingimport.repository.TrainingImportDraftDefinitionRepository;
import pl.tfhub.api.trainingimport.repository.TrainingImportDraftExecutionRepository;
import pl.tfhub.api.trainingimport.repository.TrainingImportDraftRepository;
import pl.tfhub.api.trainingimport.repository.TrainingImportDraftSegmentRepository;
import pl.tfhub.api.trainingimport.repository.TrainingImportRepository;
import pl.tfhub.api.trainingimport.repository.TrainingImportSourceImageRepository;
import pl.tfhub.api.trainingimport.config.TrainingImportProperties;
import pl.tfhub.api.user.entity.User;
import pl.tfhub.api.user.service.AccountManagementService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TrainingImportAutomaticRetryTests {

	@Test
	void schedulesTemporaryFailuresWithIncreasingDelaysAndStopsAfterThreeRetries() {
		assertScheduledRetry(0, 30_000L);
		assertScheduledRetry(1, 120_000L);
		assertScheduledRetry(2, 300_000L);
		assertScheduledRetry(3, null);
	}

	@Test
	void permanentFailuresAreNotScheduledForAutomaticRetry() {
		TrainingImport trainingImport = processingImport(0);
		TrainingImportAttempt attempt = processingAttempt(trainingImport);
		TrainingImportProcessingStateService stateService = stateService(attempt, trainingImport);

		stateService.fail(9L, null, "OUTPUT_VALIDATION_FAILED", "Invalid model response", false);

		assertEquals(TrainingImportStatus.FAILED, trainingImport.getStatus());
		assertEquals(0, trainingImport.getAutomaticRetryCount());
		assertNull(trainingImport.getNextRetryAt());
	}

	@Test
	void processingServicePropagatesRetryableFailuresToStateManagement() {
		TrainingImportProcessingStateService stateService = mock(TrainingImportProcessingStateService.class);
		GeminiTrainingRecognitionClient geminiClient = mock(GeminiTrainingRecognitionClient.class);
		ThreadPoolTaskExecutor taskExecutor = mock(ThreadPoolTaskExecutor.class);
		when(stateService.loadProcessingInput(9L)).thenReturn(
				new TrainingImportProcessingStateService.ProcessingInput(9L, 1L, 7L, new byte[] {1}));
		when(geminiClient.recognize(any())).thenThrow(
				new TrainingImportProcessingException(
						"GEMINI_UNAVAILABLE", "Gemini is temporarily unavailable", true));
		TrainingImportProcessingService processingService = new TrainingImportProcessingService(
				stateService,
				geminiClient,
				mock(TrainingImportRecognitionMapper.class),
				taskExecutor,
				new TrainingImportProperties());

		processingService.processClaimedAttempt(9L);

		verify(stateService).fail(
				9L, null, "GEMINI_UNAVAILABLE", "Gemini is temporarily unavailable", true);
	}

	@Test
	void schedulerServiceClaimsDueRetriesBeforePendingImports() {
		TrainingImportProcessingStateService stateService = mock(TrainingImportProcessingStateService.class);
		ThreadPoolTaskExecutor taskExecutor = mock(ThreadPoolTaskExecutor.class);
		ThreadPoolExecutor executor = mock(ThreadPoolExecutor.class);
		when(taskExecutor.getThreadPoolExecutor()).thenReturn(executor);
		when(executor.getActiveCount()).thenReturn(0);
		when(executor.getMaximumPoolSize()).thenReturn(1);
		when(stateService.findNextDueAutomaticRetryImportId()).thenReturn(7L);
		when(stateService.claimScheduledRetry(7L)).thenReturn(9L);
		doNothing().when(taskExecutor).execute(any(Runnable.class));
		TrainingImportProcessingService processingService = new TrainingImportProcessingService(
				stateService,
				mock(GeminiTrainingRecognitionClient.class),
				mock(TrainingImportRecognitionMapper.class),
				taskExecutor,
				new TrainingImportProperties());

		processingService.scheduleNextPendingImport();

		verify(stateService).claimScheduledRetry(7L);
		verify(stateService, never()).findNextPendingImportId();
	}

	private void assertScheduledRetry(int priorRetryCount, Long expectedDelayMillis) {
		TrainingImport trainingImport = processingImport(priorRetryCount);
		TrainingImportAttempt attempt = processingAttempt(trainingImport);
		TrainingImportProcessingStateService stateService = stateService(attempt, trainingImport);
		OffsetDateTime beforeFailure = OffsetDateTime.now(ZoneOffset.UTC);

		stateService.fail(9L, null, "GEMINI_UNAVAILABLE", "Gemini is temporarily unavailable", true);

		assertEquals(TrainingImportStatus.FAILED, trainingImport.getStatus());
		if (expectedDelayMillis == null) {
			assertEquals(3, trainingImport.getAutomaticRetryCount());
			assertNull(trainingImport.getNextRetryAt());
			return;
		}
		assertEquals(priorRetryCount + 1, trainingImport.getAutomaticRetryCount());
		long actualDelayMillis = Duration.between(beforeFailure, trainingImport.getNextRetryAt()).toMillis();
		assertTrue(Math.abs(actualDelayMillis - expectedDelayMillis) < 1_000);
	}

	private TrainingImport processingImport(int priorRetryCount) {
		TrainingImport trainingImport =
				new TrainingImport(mock(TrainingGroup.class), mock(User.class), OffsetDateTime.now(), false);
		ReflectionTestUtils.setField(trainingImport, "id", 1L);
		trainingImport.changeStatus(TrainingImportStatus.FAILED);
		for (int retry = 0; retry < priorRetryCount; retry++) {
			trainingImport.scheduleAutomaticRetry(OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(1));
		}
		trainingImport.changeStatus(TrainingImportStatus.PROCESSING);
		return trainingImport;
	}

	private TrainingImportAttempt processingAttempt(TrainingImport trainingImport) {
		TrainingImportAttempt attempt = new TrainingImportAttempt(
				trainingImport,
				1,
				null,
				"gemini-test",
				TrainingImportAttemptStatus.PROCESSING,
				OffsetDateTime.now(ZoneOffset.UTC),
				null,
				null,
				null,
				null);
		ReflectionTestUtils.setField(attempt, "id", 9L);
		return attempt;
	}

	private TrainingImportProcessingStateService stateService(
			TrainingImportAttempt attempt,
			TrainingImport trainingImport) {
		TrainingImportAttemptRepository attemptRepository = mock(TrainingImportAttemptRepository.class);
		TrainingImportRepository importRepository = mock(TrainingImportRepository.class);
		EntityManager entityManager = mock(EntityManager.class);
		when(attemptRepository.findById(9L)).thenReturn(Optional.of(attempt));
		when(importRepository.findByIdForUpdate(anyLong())).thenReturn(Optional.of(trainingImport));
		TrainingImportProcessingStateService stateService = new TrainingImportProcessingStateService(
				mock(TrainingImportAccessService.class),
				importRepository,
				attemptRepository,
				mock(TrainingImportSourceImageRepository.class),
				mock(TrainingImportDraftRepository.class),
				mock(TrainingImportDraftDefinitionRepository.class),
				mock(TrainingImportDraftSegmentRepository.class),
				mock(TrainingImportDraftAthleteRowRepository.class),
				mock(TrainingImportDraftExecutionRepository.class),
				mock(AthleteRepository.class),
				mock(pl.tfhub.api.athlete.service.AthleteService.class),
				mock(TrainingImportAthleteMatcher.class),
				mock(AccountManagementService.class),
				mock(AccessScopeService.class),
				mock(TrainingImportImageStorage.class),
				new TrainingImportProperties());
		ReflectionTestUtils.setField(stateService, "entityManager", entityManager);
		return stateService;
	}
}

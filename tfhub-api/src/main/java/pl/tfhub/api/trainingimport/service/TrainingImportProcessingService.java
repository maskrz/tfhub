package pl.tfhub.api.trainingimport.service;

import java.util.concurrent.RejectedExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Qualifier;
import pl.tfhub.api.trainingimport.config.TrainingImportProperties;
import pl.tfhub.api.trainingimport.dto.response.TrainingImportStatusResponse;
import pl.tfhub.api.trainingimport.exception.TrainingImportWorkerUnavailableException;

@Service
public class TrainingImportProcessingService {

	private static final Logger LOGGER = LoggerFactory.getLogger(TrainingImportProcessingService.class);

	private final TrainingImportProcessingStateService stateService;
	private final GeminiTrainingRecognitionClient geminiClient;
	private final TrainingImportRecognitionMapper recognitionMapper;
	private final ThreadPoolTaskExecutor taskExecutor;
	private final TrainingImportProperties properties;

	public TrainingImportProcessingService(
			TrainingImportProcessingStateService stateService,
			GeminiTrainingRecognitionClient geminiClient,
			TrainingImportRecognitionMapper recognitionMapper,
			@Qualifier("trainingImportTaskExecutor") ThreadPoolTaskExecutor trainingImportTaskExecutor,
			TrainingImportProperties properties) {
		this.stateService = stateService;
		this.geminiClient = geminiClient;
		this.recognitionMapper = recognitionMapper;
		this.taskExecutor = trainingImportTaskExecutor;
		this.properties = properties;
	}

	public TrainingImportStatusResponse getStatus(long importId) {
		return stateService.getStatus(importId);
	}

	public TrainingImportStatusResponse retry(long importId) {
		if (!properties.isProcessingEnabled() || !hasQueueCapacity()) {
			throw new TrainingImportWorkerUnavailableException();
		}
		Long attemptId = stateService.claimRetry(importId);
		dispatch(attemptId, true);
		return stateService.getStatus(importId);
	}

	public void scheduleNextPendingImport() {
		if (!properties.isProcessingEnabled() || !hasQueueCapacity()) {
			return;
		}
		Long retryImportId = stateService.findNextDueAutomaticRetryImportId();
		if (retryImportId != null) {
			Long retryAttemptId = stateService.claimScheduledRetry(retryImportId);
			if (retryAttemptId != null) {
				dispatch(retryAttemptId, false);
			}
			return;
		}
		Long importId = stateService.findNextPendingImportId();
		if (importId == null) {
			return;
		}
		Long attemptId = stateService.claimPending(importId);
		if (attemptId != null) {
			dispatch(attemptId, false);
		}
	}

	public void processClaimedAttempt(long attemptId) {
		String rawModelResponse = null;
		try {
			TrainingImportProcessingStateService.ProcessingInput input =
					stateService.loadProcessingInput(attemptId);
			if (input == null) {
				return;
			}
			GeminiTrainingRecognitionClient.GeminiRecognitionResponse response =
					geminiClient.recognize(input.image());
			rawModelResponse = response.rawResponse();
			if (response.candidateText() == null) {
				throw new TrainingImportProcessingException(
						"INVALID_MODEL_RESPONSE", "Gemini returned an invalid response");
			}
			TrainingImportRecognizedDraft draft =
					recognitionMapper.validateAndMap(response.candidateText(), input.trainingGroupId());
			stateService.complete(attemptId, rawModelResponse, draft);
		} catch (TrainingImportProcessingException exception) {
			stateService.fail(
					attemptId,
					rawModelResponse,
					exception.getFailureCode(),
					exception.getSafeMessage(),
					exception.isRetryable());
		} catch (RuntimeException exception) {
			stateService.fail(
					attemptId,
					rawModelResponse,
					"PROCESSING_FAILED",
					"Training import processing failed",
					false);
			LOGGER.error("Training import attempt {} failed with {}", attemptId,
					exception.getClass().getSimpleName());
		}
	}

	private void dispatch(long attemptId, boolean explicitRetry) {
		try {
			taskExecutor.execute(() -> processClaimedAttempt(attemptId));
		} catch (RejectedExecutionException exception) {
			stateService.fail(
					attemptId,
					null,
					"WORKER_QUEUE_FULL",
					"Training import processing could not be scheduled",
					true);
			if (explicitRetry) {
				throw new TrainingImportWorkerUnavailableException();
			}
			LOGGER.warn("Training import attempt {} could not be queued", attemptId);
		}
	}

	private boolean hasQueueCapacity() {
		try {
			var executor = taskExecutor.getThreadPoolExecutor();
			return executor.getActiveCount() < executor.getMaximumPoolSize()
					|| executor.getQueue().remainingCapacity() > 0;
		} catch (IllegalStateException exception) {
			return false;
		}
	}
}

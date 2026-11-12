package pl.tfhub.api.trainingimport.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
		prefix = "tfhub.training-import",
		name = "processing-enabled",
		havingValue = "true",
		matchIfMissing = true)
public class TrainingImportProcessingScheduler {

	private static final Logger LOGGER = LoggerFactory.getLogger(TrainingImportProcessingScheduler.class);

	private final TrainingImportProcessingStateService stateService;
	private final TrainingImportProcessingService processingService;

	public TrainingImportProcessingScheduler(
			TrainingImportProcessingStateService stateService,
			TrainingImportProcessingService processingService) {
		this.stateService = stateService;
		this.processingService = processingService;
	}

	@Scheduled(fixedDelayString = "${tfhub.training-import.processing-poll-interval-ms:5000}")
	public void pollQueue() {
		try {
			stateService.failStaleAttempts();
			processingService.scheduleNextPendingImport();
		} catch (RuntimeException exception) {
			LOGGER.error("Training import queue polling failed with {}",
					exception.getClass().getSimpleName());
		}
	}
}

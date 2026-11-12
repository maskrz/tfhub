package pl.tfhub.api.trainingimport.service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import pl.tfhub.api.common.authorization.AccessScopeService;
import pl.tfhub.api.traininggroup.entity.TrainingGroup;
import pl.tfhub.api.traininggroup.service.TrainingGroupService;
import pl.tfhub.api.trainingimport.dto.response.TrainingImportUploadResponse;
import pl.tfhub.api.trainingimport.entity.TrainingImport;
import pl.tfhub.api.trainingimport.entity.TrainingImportSourceImage;
import pl.tfhub.api.trainingimport.exception.InvalidTrainingImportUploadException;
import pl.tfhub.api.trainingimport.exception.TrainingImportImageStorageException;
import pl.tfhub.api.trainingimport.repository.TrainingImportRepository;
import pl.tfhub.api.trainingimport.repository.TrainingImportSourceImageRepository;
import pl.tfhub.api.user.entity.User;
import pl.tfhub.api.user.service.AccountManagementService;
import pl.tfhub.api.user.service.AuthenticatedUser;

@Service
public class TrainingImportUploadService {

	private static final Logger LOGGER = LoggerFactory.getLogger(TrainingImportUploadService.class);

	private final TrainingImportRepository trainingImportRepository;
	private final TrainingImportSourceImageRepository sourceImageRepository;
	private final TrainingImportImageProcessor imageProcessor;
	private final TrainingImportImageStorage imageStorage;
	private final TrainingGroupService trainingGroupService;
	private final AccountManagementService accountManagementService;
	private final AccessScopeService accessScopeService;

	public TrainingImportUploadService(
			TrainingImportRepository trainingImportRepository,
			TrainingImportSourceImageRepository sourceImageRepository,
			TrainingImportImageProcessor imageProcessor,
			TrainingImportImageStorage imageStorage,
			TrainingGroupService trainingGroupService,
			AccountManagementService accountManagementService,
			AccessScopeService accessScopeService) {
		this.trainingImportRepository = trainingImportRepository;
		this.sourceImageRepository = sourceImageRepository;
		this.imageProcessor = imageProcessor;
		this.imageStorage = imageStorage;
		this.trainingGroupService = trainingGroupService;
		this.accountManagementService = accountManagementService;
		this.accessScopeService = accessScopeService;
	}

	@Transactional
	public TrainingImportUploadResponse upload(long trainingGroupId, MultipartFile upload) {
		if (trainingGroupId <= 0) {
			throw new InvalidTrainingImportUploadException("Training group ID must be positive");
		}
		accessScopeService.requireCoachOrAdmin();
		AuthenticatedUser authenticatedUser = accessScopeService.currentUser();
		accessScopeService.requireGroupAssignment(trainingGroupId);
		TrainingGroup group = trainingGroupService.getTrainingGroupEntity(trainingGroupId);
		User initiator = accountManagementService.getAccountEntity(authenticatedUser.id());

		OptimizedTrainingImage optimized = imageProcessor.optimize(upload);
		boolean possibleDuplicate = sourceImageRepository.existsBySha256(optimized.sha256());
		byte[] jpegContents = optimized.contents();
		String storageKey = imageStorage.store(jpegContents);
		registerRollbackCleanup(storageKey);

		TrainingImport trainingImport = trainingImportRepository.saveAndFlush(new TrainingImport(
				group, initiator, OffsetDateTime.now(ZoneOffset.UTC), possibleDuplicate));
		sourceImageRepository.saveAndFlush(new TrainingImportSourceImage(
				trainingImport,
				storageKey,
				optimized.originalFilename(),
				jpegContents.length,
				optimized.width(),
				optimized.height(),
				optimized.sha256()));

		return new TrainingImportUploadResponse(
				trainingImport.getId(),
				trainingGroupId,
				trainingImport.getStatus(),
				trainingImport.isPossibleDuplicate());
	}

	private void registerRollbackCleanup(String storageKey) {
		if (!TransactionSynchronizationManager.isSynchronizationActive()) {
			imageStorage.deleteStored(storageKey);
			throw new IllegalStateException("Training import upload requires an active transaction");
		}
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCompletion(int status) {
				if (status != STATUS_COMMITTED) {
					try {
						imageStorage.deleteStored(storageKey);
					} catch (TrainingImportImageStorageException exception) {
						LOGGER.error("Could not remove image after failed training import persistence", exception);
					}
				}
			}
		});
	}
}

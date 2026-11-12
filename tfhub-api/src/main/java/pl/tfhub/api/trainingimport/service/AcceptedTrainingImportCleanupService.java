package pl.tfhub.api.trainingimport.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import pl.tfhub.api.training.service.AcceptedTrainingImportCleanup;
import pl.tfhub.api.trainingimport.entity.TrainingImport;
import pl.tfhub.api.trainingimport.repository.TrainingImportRepository;
import pl.tfhub.api.trainingimport.repository.TrainingImportSourceImageRepository;

@Service
public class AcceptedTrainingImportCleanupService implements AcceptedTrainingImportCleanup {

	private static final Logger LOGGER =
			LoggerFactory.getLogger(AcceptedTrainingImportCleanupService.class);

	private final TrainingImportRepository importRepository;
	private final TrainingImportSourceImageRepository sourceImageRepository;
	private final TrainingImportImageStorage imageStorage;

	@PersistenceContext
	private EntityManager entityManager;

	public AcceptedTrainingImportCleanupService(
			TrainingImportRepository importRepository,
			TrainingImportSourceImageRepository sourceImageRepository,
			TrainingImportImageStorage imageStorage) {
		this.importRepository = importRepository;
		this.sourceImageRepository = sourceImageRepository;
		this.imageStorage = imageStorage;
	}

	@Override
	@Transactional
	public void deleteForTraining(long trainingId) {
		TrainingImport trainingImport = importRepository
				.findByAcceptedTrainingSessionIdForUpdate(trainingId)
				.orElse(null);
		if (trainingImport == null) {
			return;
		}

		String storageKey = sourceImageRepository.findById(trainingImport.getId())
				.map(sourceImage -> sourceImage.getStorageKey())
				.orElse(null);
		long importId = trainingImport.getId();
		entityManager.flush();
		deleteByDraftId("training_import_draft_executions", importId);
		deleteByDraftId("training_import_draft_athlete_rows", importId);
		deleteByDraftId("training_import_draft_segments", importId);
		deleteByDraftId("training_import_draft_definitions", importId);
		deleteByImportId("training_import_drafts", importId);
		deleteByImportId("training_import_attempts", importId);
		deleteByImportId("training_import_source_images", importId);
		entityManager.createNativeQuery("DELETE FROM training_imports WHERE id = :importId")
				.setParameter("importId", importId)
				.executeUpdate();

		if (storageKey != null) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					try {
						imageStorage.deleteStored(storageKey);
					} catch (RuntimeException exception) {
						LOGGER.warn("Could not remove source image for deleted training import {}", importId);
					}
				}
			});
		}
	}

	private void deleteByImportId(String tableName, long importId) {
		entityManager.createNativeQuery("DELETE FROM " + tableName
						+ " WHERE import_id = :importId")
				.setParameter("importId", importId)
				.executeUpdate();
	}

	private void deleteByDraftId(String tableName, long importId) {
		entityManager.createNativeQuery("DELETE FROM " + tableName
						+ " WHERE draft_id IN (SELECT id FROM training_import_drafts WHERE import_id = :importId)")
				.setParameter("importId", importId)
				.executeUpdate();
	}
}

package pl.tfhub.api.trainingimport.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.tfhub.api.trainingimport.entity.TrainingImport;
import pl.tfhub.api.trainingimport.exception.TrainingImportImageStorageException;
import pl.tfhub.api.trainingimport.repository.TrainingImportSourceImageRepository;

@Service
public class TrainingImportSourceImageService {

	private final TrainingImportAccessService accessService;
	private final TrainingImportSourceImageRepository sourceImageRepository;
	private final TrainingImportImageStorage imageStorage;

	public TrainingImportSourceImageService(
			TrainingImportAccessService accessService,
			TrainingImportSourceImageRepository sourceImageRepository,
			TrainingImportImageStorage imageStorage) {
		this.accessService = accessService;
		this.sourceImageRepository = sourceImageRepository;
		this.imageStorage = imageStorage;
	}

	@Transactional(readOnly = true)
	public byte[] getSourceImage(long importId) {
		TrainingImport trainingImport = accessService.getAccessibleImport(importId);
		var sourceImage = sourceImageRepository.findById(trainingImport.getId())
				.orElseThrow(() -> new TrainingImportImageStorageException(
						new IllegalStateException("Training import source image metadata is missing")));
		return imageStorage.load(sourceImage.getStorageKey());
	}
}

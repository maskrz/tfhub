package pl.tfhub.api.trainingimport.service;

import java.util.Set;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import pl.tfhub.api.trainingimport.repository.TrainingImportSourceImageRepository;

@Component
public class TrainingImportImageStorageRecovery implements ApplicationRunner {

	private final TrainingImportSourceImageRepository sourceImageRepository;
	private final TrainingImportImageStorage imageStorage;

	public TrainingImportImageStorageRecovery(
			TrainingImportSourceImageRepository sourceImageRepository,
			TrainingImportImageStorage imageStorage) {
		this.sourceImageRepository = sourceImageRepository;
		this.imageStorage = imageStorage;
	}

	@Override
	public void run(ApplicationArguments arguments) {
		imageStorage.removeOrphanFiles(Set.copyOf(sourceImageRepository.findAllStorageKeys()));
	}
}

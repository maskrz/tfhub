package pl.tfhub.api.trainingimport.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import pl.tfhub.api.trainingimport.entity.TrainingImportSourceImage;

public interface TrainingImportSourceImageRepository
		extends JpaRepository<TrainingImportSourceImage, Long> {

	boolean existsBySha256(String sha256);

	long countBySha256(String sha256);

	@Query("SELECT source.storageKey FROM TrainingImportSourceImage source")
	List<String> findAllStorageKeys();
}

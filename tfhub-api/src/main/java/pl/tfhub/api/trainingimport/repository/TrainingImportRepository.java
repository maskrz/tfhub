package pl.tfhub.api.trainingimport.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.tfhub.api.trainingimport.entity.TrainingImport;

public interface TrainingImportRepository extends JpaRepository<TrainingImport, Long> {
}

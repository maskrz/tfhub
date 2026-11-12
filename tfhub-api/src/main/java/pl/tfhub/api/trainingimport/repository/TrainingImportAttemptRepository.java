package pl.tfhub.api.trainingimport.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.tfhub.api.trainingimport.entity.TrainingImportAttempt;

public interface TrainingImportAttemptRepository extends JpaRepository<TrainingImportAttempt, Long> {
}

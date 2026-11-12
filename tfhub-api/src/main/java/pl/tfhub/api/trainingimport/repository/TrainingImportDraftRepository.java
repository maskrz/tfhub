package pl.tfhub.api.trainingimport.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import pl.tfhub.api.trainingimport.entity.TrainingImportDraft;

public interface TrainingImportDraftRepository extends JpaRepository<TrainingImportDraft, Long> {

	Optional<TrainingImportDraft> findByTrainingImport_Id(long importId);
}

package pl.tfhub.api.trainingimport.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import pl.tfhub.api.trainingimport.entity.TrainingImportDraftSection;

public interface TrainingImportDraftSectionRepository
		extends JpaRepository<TrainingImportDraftSection, Long> {

	List<TrainingImportDraftSection> findByDraft_IdOrderBySequenceNumberAsc(long draftId);
}

package pl.tfhub.api.trainingimport.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pl.tfhub.api.trainingimport.entity.TrainingImportDraftDefinition;

public interface TrainingImportDraftDefinitionRepository
		extends JpaRepository<TrainingImportDraftDefinition, Long> {

	List<TrainingImportDraftDefinition> findByDraft_IdOrderBySequenceNumberAsc(long draftId);

	@Modifying
	@Query("DELETE FROM TrainingImportDraftDefinition definition WHERE definition.draft.id = :draftId")
	void deleteByDraftId(@Param("draftId") long draftId);
}

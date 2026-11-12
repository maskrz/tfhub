package pl.tfhub.api.trainingimport.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pl.tfhub.api.trainingimport.entity.TrainingImportDraftExecution;

public interface TrainingImportDraftExecutionRepository
		extends JpaRepository<TrainingImportDraftExecution, Long> {

	List<TrainingImportDraftExecution> findByDraft_IdOrderByIdAsc(long draftId);

	@Modifying
	@Query("DELETE FROM TrainingImportDraftExecution execution WHERE execution.draft.id = :draftId")
	void deleteByDraftId(@Param("draftId") long draftId);
}

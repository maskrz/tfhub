package pl.tfhub.api.trainingimport.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pl.tfhub.api.trainingimport.entity.TrainingImportDraftSegment;

public interface TrainingImportDraftSegmentRepository
		extends JpaRepository<TrainingImportDraftSegment, Long> {

	List<TrainingImportDraftSegment> findByDefinition_IdOrderBySequenceNumberAsc(long definitionId);

	@Modifying
	@Query("DELETE FROM TrainingImportDraftSegment segment WHERE segment.draft.id = :draftId")
	void deleteByDraftId(@Param("draftId") long draftId);
}

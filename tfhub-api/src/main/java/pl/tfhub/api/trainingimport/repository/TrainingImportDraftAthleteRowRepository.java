package pl.tfhub.api.trainingimport.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pl.tfhub.api.trainingimport.entity.TrainingImportDraftAthleteRow;

public interface TrainingImportDraftAthleteRowRepository
		extends JpaRepository<TrainingImportDraftAthleteRow, Long> {

	List<TrainingImportDraftAthleteRow> findByDefinition_IdOrderByIdAsc(long definitionId);

	List<TrainingImportDraftAthleteRow> findByDraft_IdOrderByIdAsc(long draftId);

	@Modifying
	@Query("DELETE FROM TrainingImportDraftAthleteRow athleteRow WHERE athleteRow.draft.id = :draftId")
	void deleteByDraftId(@Param("draftId") long draftId);
}

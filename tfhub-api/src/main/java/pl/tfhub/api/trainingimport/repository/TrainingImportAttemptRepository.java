package pl.tfhub.api.trainingimport.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import pl.tfhub.api.trainingimport.entity.TrainingImportAttempt;
import pl.tfhub.api.trainingimport.enums.TrainingImportAttemptStatus;

public interface TrainingImportAttemptRepository extends JpaRepository<TrainingImportAttempt, Long> {

	Optional<TrainingImportAttempt> findFirstByTrainingImport_IdOrderByAttemptNumberDesc(long importId);

	Optional<TrainingImportAttempt> findByIdAndTrainingImport_Id(long attemptId, long importId);

	@Query("""
			SELECT COALESCE(MAX(attempt.attemptNumber), 0)
			FROM TrainingImportAttempt attempt
			WHERE attempt.trainingImport.id = :importId
			""")
	int findMaximumAttemptNumber(@Param("importId") long importId);

	List<TrainingImportAttempt> findByStatusAndStartedAtBefore(
			TrainingImportAttemptStatus status,
			OffsetDateTime startedBefore);
}

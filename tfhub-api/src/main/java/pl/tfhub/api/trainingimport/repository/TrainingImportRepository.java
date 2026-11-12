package pl.tfhub.api.trainingimport.repository;

import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.OffsetDateTime;
import jakarta.persistence.LockModeType;
import pl.tfhub.api.trainingimport.entity.TrainingImport;
import pl.tfhub.api.trainingimport.enums.TrainingImportStatus;

public interface TrainingImportRepository extends JpaRepository<TrainingImport, Long> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT trainingImport FROM TrainingImport trainingImport WHERE trainingImport.id = :id")
	Optional<TrainingImport> findByIdForUpdate(@Param("id") long id);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
			SELECT trainingImport FROM TrainingImport trainingImport
			WHERE trainingImport.acceptedTrainingSessionId = :trainingId
			""")
	Optional<TrainingImport> findByAcceptedTrainingSessionIdForUpdate(
			@Param("trainingId") long trainingId);

	@Query("""
			SELECT trainingImport.id
			FROM TrainingImport trainingImport
			WHERE trainingImport.status = :status
			ORDER BY trainingImport.createdAt, trainingImport.id
			""")
	java.util.List<Long> findIdsByStatusOrderByCreatedAt(
			@Param("status") TrainingImportStatus status,
			Pageable pageable);

	@Query("""
			SELECT trainingImport.id
			FROM TrainingImport trainingImport
			WHERE trainingImport.status = :status
				AND trainingImport.nextRetryAt <= :retryCutoff
			ORDER BY trainingImport.nextRetryAt, trainingImport.createdAt, trainingImport.id
			""")
	java.util.List<Long> findDueAutomaticRetryIds(
			@Param("status") TrainingImportStatus status,
			@Param("retryCutoff") OffsetDateTime retryCutoff,
			Pageable pageable);
}

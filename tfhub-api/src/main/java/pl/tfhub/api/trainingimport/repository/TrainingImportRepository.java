package pl.tfhub.api.trainingimport.repository;

import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import pl.tfhub.api.trainingimport.entity.TrainingImport;
import pl.tfhub.api.trainingimport.enums.TrainingImportStatus;

public interface TrainingImportRepository extends JpaRepository<TrainingImport, Long> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT trainingImport FROM TrainingImport trainingImport WHERE trainingImport.id = :id")
	Optional<TrainingImport> findByIdForUpdate(@Param("id") long id);

	@Query("""
			SELECT trainingImport.id
			FROM TrainingImport trainingImport
			WHERE trainingImport.status = :status
			ORDER BY trainingImport.createdAt, trainingImport.id
			""")
	java.util.List<Long> findIdsByStatusOrderByCreatedAt(
			@Param("status") TrainingImportStatus status,
			Pageable pageable);
}

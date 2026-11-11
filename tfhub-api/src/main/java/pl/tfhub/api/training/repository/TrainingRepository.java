package pl.tfhub.api.training.repository;

import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pl.tfhub.api.training.entity.Training;

public interface TrainingRepository extends JpaRepository<Training, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select training from Training training where training.id = :id")
    Optional<Training> findByIdForUpdate(@Param("id") Long id);
}

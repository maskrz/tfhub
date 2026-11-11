package pl.tfhub.api.training.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.tfhub.api.training.entity.TrainingSegmentExecution;

public interface TrainingSegmentExecutionRepository extends JpaRepository<TrainingSegmentExecution, Long> {
}

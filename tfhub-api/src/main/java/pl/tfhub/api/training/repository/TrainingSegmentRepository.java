package pl.tfhub.api.training.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.tfhub.api.training.entity.TrainingSegment;

public interface TrainingSegmentRepository extends JpaRepository<TrainingSegment, Long> {
}

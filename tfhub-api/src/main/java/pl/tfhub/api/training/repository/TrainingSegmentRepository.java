package pl.tfhub.api.training.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import pl.tfhub.api.training.entity.TrainingSegment;

public interface TrainingSegmentRepository extends JpaRepository<TrainingSegment, Long> {

    List<TrainingSegment> findAllByTrainingDefinition_IdInOrderByTrainingDefinition_IdAscSequenceNumberAsc(
            List<Long> definitionIds);
}

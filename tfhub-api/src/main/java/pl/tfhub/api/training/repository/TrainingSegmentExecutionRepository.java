package pl.tfhub.api.training.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import pl.tfhub.api.training.entity.TrainingSegmentExecution;

public interface TrainingSegmentExecutionRepository extends JpaRepository<TrainingSegmentExecution, Long> {

    List<TrainingSegmentExecution> findAllByParticipation_IdIn(List<Long> participationIds);
}

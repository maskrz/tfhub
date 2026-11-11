package pl.tfhub.api.training.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import pl.tfhub.api.training.entity.TrainingDefinition;

public interface TrainingDefinitionRepository extends JpaRepository<TrainingDefinition, Long> {

    List<TrainingDefinition> findAllByTraining_IdOrderBySequenceNumberAsc(Long trainingId);
}

package pl.tfhub.api.training.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.tfhub.api.training.entity.TrainingDefinition;

public interface TrainingDefinitionRepository extends JpaRepository<TrainingDefinition, Long> {
}

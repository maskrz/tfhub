package pl.tfhub.api.traininggroup.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.tfhub.api.traininggroup.entity.TrainingGroup;

public interface TrainingGroupRepository extends JpaRepository<TrainingGroup, Long> {
}

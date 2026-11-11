package pl.tfhub.api.training.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.tfhub.api.training.entity.Training;

public interface TrainingRepository extends JpaRepository<Training, Long> {
}

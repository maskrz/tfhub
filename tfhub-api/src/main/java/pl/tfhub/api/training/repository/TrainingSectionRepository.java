package pl.tfhub.api.training.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import pl.tfhub.api.training.entity.TrainingSection;

public interface TrainingSectionRepository extends JpaRepository<TrainingSection, Long> {

    List<TrainingSection> findAllByTraining_IdOrderBySequenceNumberAsc(Long trainingId);
}

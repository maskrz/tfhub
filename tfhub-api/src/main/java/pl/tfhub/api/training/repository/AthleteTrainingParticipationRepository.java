package pl.tfhub.api.training.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.tfhub.api.training.entity.AthleteTrainingParticipation;

public interface AthleteTrainingParticipationRepository extends JpaRepository<AthleteTrainingParticipation, Long> {
}

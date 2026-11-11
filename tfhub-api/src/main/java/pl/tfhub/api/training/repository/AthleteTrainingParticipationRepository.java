package pl.tfhub.api.training.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pl.tfhub.api.training.entity.AthleteTrainingParticipation;

public interface AthleteTrainingParticipationRepository extends JpaRepository<AthleteTrainingParticipation, Long> {

    List<AthleteTrainingParticipation> findAllByTraining_IdOrderByTrainingDefinition_IdAscIdAsc(Long trainingId);

    @Query("""
            select participation
            from AthleteTrainingParticipation participation
            join fetch participation.athlete athlete
            where participation.training.id = :trainingId
            order by athlete.lastName, athlete.firstName, participation.id
            """)
    List<AthleteTrainingParticipation> findAllDetailsByTrainingId(@Param("trainingId") Long trainingId);
}

package pl.tfhub.api.training.repository;

import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class TrainingCreationRepository {

    private final JdbcClient jdbcClient;

    public TrainingCreationRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public List<AthleteTrainingGroup> findAthleteTrainingGroups(List<Long> athleteIds) {
        if (athleteIds.isEmpty()) {
            return List.of();
        }
        return jdbcClient.sql("""
                        SELECT id, training_group_id
                        FROM athletes
                        WHERE id IN (:athleteIds)
                        ORDER BY id
                        FOR SHARE
                        """)
                .param("athleteIds", athleteIds)
                .query((resultSet, rowNumber) -> new AthleteTrainingGroup(
                        resultSet.getLong("id"),
                        resultSet.getObject("training_group_id", Long.class)))
                .list();
    }

    public record AthleteTrainingGroup(long athleteId, Long trainingGroupId) {
    }
}

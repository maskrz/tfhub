package pl.tfhub.api.training.repository;

import java.time.LocalDate;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import pl.tfhub.api.training.dto.response.TrainingListItemResponse;
import pl.tfhub.api.traininggroup.dto.response.TrainingGroupResponse;

@Repository
public class TrainingListRepository {

    private static final String BASE_QUERY = """
            SELECT
                t.id,
                t.name,
                t.training_date,
                g.id AS training_group_id,
                g.name AS training_group_name,
                (SELECT COUNT(*)
                 FROM training_definitions d
                 WHERE d.training_session_id = t.id) AS definition_count,
                (SELECT COUNT(*)
                 FROM training_athlete_participations p
                 WHERE p.training_session_id = t.id) AS participant_count
            FROM training_sessions t
            JOIN training_groups g ON g.id = t.training_group_id
            WHERE t.active = TRUE
            """;

    private final JdbcClient jdbcClient;

    public TrainingListRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public List<TrainingListItemResponse> findAllActive() {
        return jdbcClient.sql(BASE_QUERY + """
                        ORDER BY t.training_date DESC, t.id DESC
                        """)
                .query((resultSet, rowNumber) -> mapTraining(resultSet.getLong("id"),
                        resultSet.getString("name"),
                        resultSet.getObject("training_date", LocalDate.class),
                        resultSet.getLong("training_group_id"),
                        resultSet.getString("training_group_name"),
                        resultSet.getLong("definition_count"),
                        resultSet.getLong("participant_count")))
                .list();
    }

    public List<TrainingListItemResponse> findActiveForCoach(long coachId) {
        return jdbcClient.sql(BASE_QUERY + """
                        AND EXISTS (
                            SELECT 1
                            FROM user_training_group ug
                            WHERE ug.training_group_id = t.training_group_id
                              AND ug.user_id = :coachId
                        )
                        ORDER BY t.training_date DESC, t.id DESC
                        """)
                .param("coachId", coachId)
                .query((resultSet, rowNumber) -> mapTraining(resultSet.getLong("id"),
                        resultSet.getString("name"),
                        resultSet.getObject("training_date", LocalDate.class),
                        resultSet.getLong("training_group_id"),
                        resultSet.getString("training_group_name"),
                        resultSet.getLong("definition_count"),
                        resultSet.getLong("participant_count")))
                .list();
    }

    private TrainingListItemResponse mapTraining(
            long id,
            String name,
            LocalDate trainingDate,
            long groupId,
            String groupName,
            long definitionCount,
            long participantCount) {
        return new TrainingListItemResponse(id, name, trainingDate,
                new TrainingGroupResponse(groupId, groupName), definitionCount, participantCount);
    }
}

package pl.tfhub.api.training.repository;

import java.time.LocalDate;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import pl.tfhub.api.training.dto.response.TrainingListItemResponse;
import pl.tfhub.api.training.enums.TrainingStatus;
import pl.tfhub.api.traininggroup.dto.response.TrainingGroupResponse;

@Repository
public class TrainingListRepository {

    private static final String BASE_QUERY = """
            SELECT
                t.id,
                t.name,
                t.training_date,
                t.status,
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
            """;

    private final JdbcClient jdbcClient;

    public TrainingListRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public List<TrainingListItemResponse> findAll(boolean includeCompleted) {
        String query = BASE_QUERY + (includeCompleted ? "" : "WHERE t.status = 'PLANNED'\n") + """
                        ORDER BY t.training_date DESC, t.id DESC
                        """;
        return jdbcClient.sql(query)
                .query((resultSet, rowNumber) -> mapTraining(resultSet.getLong("id"),
                        resultSet.getString("name"),
                        resultSet.getObject("training_date", LocalDate.class),
                        TrainingStatus.valueOf(resultSet.getString("status")),
                        resultSet.getLong("training_group_id"),
                        resultSet.getString("training_group_name"),
                        resultSet.getLong("definition_count"),
                        resultSet.getLong("participant_count")))
                .list();
    }

    public List<TrainingListItemResponse> findForCoach(long coachId, boolean includeCompleted) {
        String statusFilter = includeCompleted ? "" : "AND t.status = 'PLANNED'\n";
        return jdbcClient.sql(BASE_QUERY + "WHERE EXISTS (\n" + """
                            SELECT 1
                            FROM user_training_group ug
                            WHERE ug.training_group_id = t.training_group_id
                              AND ug.user_id = :coachId
                        )
                        """ + statusFilter + """
                        ORDER BY t.training_date DESC, t.id DESC
                        """)
                .param("coachId", coachId)
                .query((resultSet, rowNumber) -> mapTraining(resultSet.getLong("id"),
                        resultSet.getString("name"),
                        resultSet.getObject("training_date", LocalDate.class),
                        TrainingStatus.valueOf(resultSet.getString("status")),
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
            TrainingStatus status,
            long groupId,
            String groupName,
            long definitionCount,
            long participantCount) {
        return new TrainingListItemResponse(id, name, trainingDate,
                new TrainingGroupResponse(groupId, groupName), definitionCount, participantCount, status);
    }
}

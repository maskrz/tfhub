package pl.tfhub.api.training.repository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import pl.tfhub.api.training.dto.response.TrainingListItemResponse;
import pl.tfhub.api.training.enums.TrainingStatus;
import pl.tfhub.api.training.enums.TrainingType;
import pl.tfhub.api.traininggroup.dto.response.TrainingGroupResponse;

@Repository
public class TrainingListRepository {

    private static final String BASE_QUERY = """
            SELECT
                t.id,
                t.name,
                t.training_date,
                t.status,
                t.training_type,
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

    public List<TrainingListItemResponse> findAll(boolean includeCompleted, TrainingType trainingType) {
        String query = buildQuery(includeCompleted, trainingType, null);
        var sql = jdbcClient.sql(query);
        if (trainingType != null) {
            sql = sql.param("trainingType", trainingType.name());
        }
        return sql.query((resultSet, rowNumber) -> mapTraining(resultSet.getLong("id"),
                resultSet.getString("name"),
                resultSet.getObject("training_date", LocalDate.class),
                TrainingStatus.valueOf(resultSet.getString("status")),
                TrainingType.valueOf(resultSet.getString("training_type")),
                resultSet.getLong("training_group_id"),
                resultSet.getString("training_group_name"),
                resultSet.getLong("definition_count"),
                resultSet.getLong("participant_count")))
                .list();
    }

    public List<TrainingListItemResponse> findForCoach(
            long coachId,
            boolean includeCompleted,
            TrainingType trainingType) {
        String query = buildQuery(includeCompleted, trainingType, """
                EXISTS (
                    SELECT 1
                    FROM user_training_group ug
                    WHERE ug.training_group_id = t.training_group_id
                      AND ug.user_id = :coachId
                )
                """);
        var sql = jdbcClient.sql(query).param("coachId", coachId);
        if (trainingType != null) {
            sql = sql.param("trainingType", trainingType.name());
        }
        return sql.query((resultSet, rowNumber) -> mapTraining(resultSet.getLong("id"),
                resultSet.getString("name"),
                resultSet.getObject("training_date", LocalDate.class),
                TrainingStatus.valueOf(resultSet.getString("status")),
                TrainingType.valueOf(resultSet.getString("training_type")),
                resultSet.getLong("training_group_id"),
                resultSet.getString("training_group_name"),
                resultSet.getLong("definition_count"),
                resultSet.getLong("participant_count")))
                .list();
    }

    private String buildQuery(boolean includeCompleted, TrainingType trainingType, String coachPredicate) {
        List<String> predicates = new ArrayList<>();
        if (coachPredicate != null) {
            predicates.add(coachPredicate);
        }
        if (!includeCompleted) {
            predicates.add("t.status = 'PLANNED'");
        }
        if (trainingType != null) {
            predicates.add("t.training_type = :trainingType");
        }
        String query = BASE_QUERY;
        if (!predicates.isEmpty()) {
            query += "WHERE " + String.join(" AND ", predicates) + "\n";
        }
        return query + "ORDER BY t.training_date DESC, t.id DESC";
    }

    private TrainingListItemResponse mapTraining(
            long id,
            String name,
            LocalDate trainingDate,
            TrainingStatus status,
            TrainingType trainingType,
            long groupId,
            String groupName,
            long definitionCount,
            long participantCount) {
        return new TrainingListItemResponse(
                id,
                name,
                trainingDate,
                new TrainingGroupResponse(groupId, groupName),
                definitionCount,
                participantCount,
                status,
                trainingType);
    }
}

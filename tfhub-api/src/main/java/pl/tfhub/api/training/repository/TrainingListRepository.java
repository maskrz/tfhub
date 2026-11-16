package pl.tfhub.api.training.repository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import pl.tfhub.api.training.dto.response.TrainingListItemResponse;
import pl.tfhub.api.training.enums.TrainingStatus;
import pl.tfhub.api.training.enums.TrainingAssignmentType;
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
                t.assignment_type,
                g.id AS training_group_id,
                g.name AS training_group_name,
                (SELECT COUNT(*)
                 FROM training_session_athletes tsa
                 WHERE tsa.training_session_id = t.id) AS assigned_athlete_count,
                (SELECT COUNT(*)
                 FROM training_definitions d
                 WHERE d.training_session_id = t.id) AS definition_count,
                (SELECT COUNT(*)
                 FROM training_athlete_participations p
                 WHERE p.training_session_id = t.id) AS participant_count
            FROM training_sessions t
            LEFT JOIN training_groups g ON g.id = t.training_group_id
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
        return sql.query((resultSet, rowNumber) -> mapTraining(resultSet)).list();
    }

    public List<TrainingListItemResponse> findForCoach(
            long coachId,
            boolean includeCompleted,
            TrainingType trainingType) {
        String query = buildQuery(includeCompleted, trainingType, """
                (
                    (t.assignment_type = 'GROUP' AND EXISTS (
                        SELECT 1
                        FROM user_training_group ug
                        WHERE ug.training_group_id = t.training_group_id
                          AND ug.user_id = :coachId
                    ))
                    OR (t.training_type = 'FREE_TEXT'
                        AND t.assignment_type = 'ATHLETES'
                        AND EXISTS (
                            SELECT 1
                            FROM training_session_athletes tsa
                            WHERE tsa.training_session_id = t.id
                        )
                        AND NOT EXISTS (
                            SELECT 1
                            FROM training_session_athletes tsa
                            JOIN athletes a ON a.id = tsa.athlete_id
                            WHERE tsa.training_session_id = t.id
                              AND (a.training_group_id IS NULL OR NOT EXISTS (
                                  SELECT 1
                                  FROM user_training_group ug
                                  WHERE ug.user_id = :coachId
                                    AND ug.training_group_id = a.training_group_id
                              ))
                        ))
                )
                """);
        var sql = jdbcClient.sql(query).param("coachId", coachId);
        if (trainingType != null) {
            sql = sql.param("trainingType", trainingType.name());
        }
        return sql.query((resultSet, rowNumber) -> mapTraining(resultSet)).list();
    }

    public List<TrainingListItemResponse> findForAthlete(
            long athleteId,
            boolean includeCompleted,
            TrainingType trainingType) {
        List<String> predicates = new ArrayList<>();
        predicates.add("t.training_type = 'FREE_TEXT'");
        predicates.add("""
                EXISTS (
                    SELECT 1
                    FROM athletes a
                    WHERE a.id = :athleteId
                      AND (
                          (t.assignment_type = 'GROUP' AND t.training_group_id = a.training_group_id)
                          OR (t.assignment_type = 'ATHLETES' AND EXISTS (
                              SELECT 1
                              FROM training_session_athletes tsa
                              WHERE tsa.training_session_id = t.id
                                AND tsa.athlete_id = a.id
                          ))
                      )
                )
                """);
        if (!includeCompleted) {
            predicates.add("t.status = 'PLANNED'");
        }
        if (trainingType != null && trainingType != TrainingType.FREE_TEXT) {
            return List.of();
        }
        String query = BASE_QUERY + "WHERE " + String.join(" AND ", predicates) + "\n"
                + "ORDER BY t.training_date DESC NULLS FIRST, t.id DESC";
        return jdbcClient.sql(query)
                .param("athleteId", athleteId)
                .query((resultSet, rowNumber) -> mapTraining(resultSet))
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
        return query + "ORDER BY t.training_date DESC NULLS FIRST, t.id DESC";
    }

    private TrainingListItemResponse mapTraining(java.sql.ResultSet resultSet)
            throws java.sql.SQLException {
        Long groupId = resultSet.getObject("training_group_id", Long.class);
        TrainingGroupResponse group = groupId == null
                ? null
                : new TrainingGroupResponse(groupId, resultSet.getString("training_group_name"));
        return new TrainingListItemResponse(
                resultSet.getLong("id"),
                resultSet.getString("name"),
                resultSet.getObject("training_date", LocalDate.class),
                group,
                resultSet.getLong("definition_count"),
                resultSet.getLong("participant_count"),
                TrainingStatus.valueOf(resultSet.getString("status")),
                TrainingType.valueOf(resultSet.getString("training_type")),
                TrainingAssignmentType.valueOf(resultSet.getString("assignment_type")),
                resultSet.getLong("assigned_athlete_count"));
    }
}

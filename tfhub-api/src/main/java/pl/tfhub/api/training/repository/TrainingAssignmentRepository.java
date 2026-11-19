package pl.tfhub.api.training.repository;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import pl.tfhub.api.training.dto.response.TrainingAssignedAthleteResponse;

@Repository
public class TrainingAssignmentRepository {

    private final JdbcClient jdbcClient;

    public TrainingAssignmentRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public void replaceAthletes(long trainingId, List<Long> athleteIds) {
        deleteForTraining(trainingId);
        for (Long athleteId : athleteIds) {
            jdbcClient.sql("""
                            INSERT INTO training_session_athletes (training_session_id, athlete_id)
                            VALUES (:trainingId, :athleteId)
                            """)
                    .param("trainingId", trainingId)
                    .param("athleteId", athleteId)
                    .update();
        }
    }

    public void deleteForTraining(long trainingId) {
        jdbcClient.sql("DELETE FROM training_session_athletes WHERE training_session_id = :trainingId")
                .param("trainingId", trainingId)
                .update();
    }

    public List<Long> findAthleteIds(long trainingId) {
        return jdbcClient.sql("""
                        SELECT athlete_id
                        FROM training_session_athletes
                        WHERE training_session_id = :trainingId
                        ORDER BY athlete_id
                        """)
                .param("trainingId", trainingId)
                .query(Long.class)
                .list();
    }

    public List<TrainingAssignedAthleteResponse> findAssignedAthletes(long trainingId) {
        return jdbcClient.sql("""
                        SELECT a.id, a.first_name, a.last_name
                        FROM training_session_athletes tsa
                        JOIN athletes a ON a.id = tsa.athlete_id
                        WHERE tsa.training_session_id = :trainingId
                        ORDER BY LOWER(a.last_name), LOWER(a.first_name), a.id
                        """)
                .param("trainingId", trainingId)
                .query((resultSet, rowNumber) -> new TrainingAssignedAthleteResponse(
                        resultSet.getLong("id"),
                        resultSet.getString("first_name"),
                        resultSet.getString("last_name")))
                .list();
    }

    public Map<Long, List<TrainingAssignedAthleteResponse>> findAssignedAthletesForTrainings(List<Long> trainingIds) {
        Map<Long, List<TrainingAssignedAthleteResponse>> result = new HashMap<>();
        jdbcClient.sql("""
                        SELECT tsa.training_session_id, a.id, a.first_name, a.last_name
                        FROM training_session_athletes tsa
                        JOIN athletes a ON a.id = tsa.athlete_id
                        WHERE tsa.training_session_id IN (:trainingIds)
                        ORDER BY tsa.training_session_id, LOWER(a.last_name), LOWER(a.first_name), a.id
                        """)
                .param("trainingIds", trainingIds)
                .query((row, rowNumber) -> {
                    result.computeIfAbsent(row.getLong("training_session_id"), ignored -> new ArrayList<>())
                            .add(new TrainingAssignedAthleteResponse(
                                    row.getLong("id"), row.getString("first_name"), row.getString("last_name")));
                    return rowNumber;
                }).list();
        return result;
    }

    public boolean isVisibleToAthlete(long trainingId, long athleteId) {
        return jdbcClient.sql("""
                        SELECT EXISTS (
                            SELECT 1
                            FROM training_sessions t
                            JOIN athletes a ON a.id = :athleteId
                            WHERE t.id = :trainingId
                              AND t.training_type = 'FREE_TEXT'
                              AND (
                                  (t.assignment_type = 'GROUP'
                                      AND t.training_group_id = a.training_group_id)
                                  OR (t.assignment_type = 'ATHLETES'
                                      AND EXISTS (
                                          SELECT 1
                                          FROM training_session_athletes tsa
                                          WHERE tsa.training_session_id = t.id
                                            AND tsa.athlete_id = a.id
                                      ))
                              )
                        )
                        """)
                .param("trainingId", trainingId)
                .param("athleteId", athleteId)
                .query(Boolean.class)
                .single();
    }

    public boolean canCoachManage(long trainingId, long coachId) {
        return jdbcClient.sql("""
                        SELECT EXISTS (
                            SELECT 1
                            FROM training_sessions t
                            WHERE t.id = :trainingId
                              AND (
                                  (t.assignment_type = 'GROUP'
                                      AND EXISTS (
                                          SELECT 1
                                          FROM user_training_group ug
                                          WHERE ug.user_id = :coachId
                                            AND ug.training_group_id = t.training_group_id
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
                        )
                        """)
                .param("trainingId", trainingId)
                .param("coachId", coachId)
                .query(Boolean.class)
                .single();
    }

    public long countAssignedAthletes(long trainingId) {
        return jdbcClient.sql("""
                        SELECT COUNT(*)
                        FROM training_session_athletes
                        WHERE training_session_id = :trainingId
                        """)
                .param("trainingId", trainingId)
                .query(Long.class)
                .single();
    }
}

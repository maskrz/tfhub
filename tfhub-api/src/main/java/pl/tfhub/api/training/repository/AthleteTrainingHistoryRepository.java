package pl.tfhub.api.training.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import pl.tfhub.api.training.enums.TrainingExecutionStatus;

@Repository
public class AthleteTrainingHistoryRepository {

    private final JdbcClient jdbcClient;

    public AthleteTrainingHistoryRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public List<Row> findHistory(long athleteId, LocalDate fromDate, LocalDate toDate) {
        StringBuilder sql = new StringBuilder("""
                SELECT t.id AS training_id, t.training_date, t.training_group_id,
                       s.id AS segment_id, s.sequence_number, s.distance_m,
                       COUNT(*) OVER (PARTITION BY p.id) AS segment_count,
                       e.status, e.measured_result, e.uses_spikes
                FROM training_athlete_participations p
                JOIN training_sessions t ON t.id = p.training_session_id
                JOIN training_segments s ON s.training_definition_id = p.training_definition_id
                LEFT JOIN training_segment_executions e
                    ON e.participation_id = p.id AND e.training_segment_id = s.id
                WHERE p.athlete_id = :athleteId
                  AND EXISTS (
                      SELECT 1 FROM training_segment_executions qualifying
                      WHERE qualifying.participation_id = p.id AND qualifying.status <> 'PENDING'
                  )
                """);
        if (fromDate != null) sql.append(" AND t.training_date >= :fromDate");
        if (toDate != null) sql.append(" AND t.training_date <= :toDate");
        sql.append(" ORDER BY t.training_date DESC, t.id DESC, s.sequence_number ASC, s.id ASC");

        JdbcClient.StatementSpec statement = jdbcClient.sql(sql.toString()).param("athleteId", athleteId);
        if (fromDate != null) statement = statement.param("fromDate", fromDate);
        if (toDate != null) statement = statement.param("toDate", toDate);
        return statement.query((rs, rowNumber) -> {
            String status = rs.getString("status");
            if (status == null) {
                throw new IllegalStateException("Missing training execution for participation segment");
            }
            return new Row(
                    rs.getLong("training_id"),
                    rs.getObject("training_date", LocalDate.class),
                    rs.getLong("training_group_id"),
                    rs.getLong("segment_id"),
                    rs.getInt("sequence_number"),
                    rs.getInt("distance_m"),
                    rs.getInt("segment_count"),
                    TrainingExecutionStatus.valueOf(status),
                    rs.getBigDecimal("measured_result"),
                    rs.getBoolean("uses_spikes"));
        }).list();
    }

    public record Row(
            long trainingId,
            LocalDate trainingDate,
            long trainingGroupId,
            long segmentId,
            int sequenceNumber,
            int distanceM,
            int segmentCount,
            TrainingExecutionStatus status,
            BigDecimal measuredResult,
            boolean usesSpikes) {
    }
}

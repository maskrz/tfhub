package pl.tfhub.api.training.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import pl.tfhub.api.athlete.entity.Athlete;
import pl.tfhub.api.athlete.enums.Gender;
import pl.tfhub.api.athlete.repository.AthleteRepository;
import pl.tfhub.api.training.entity.AthleteTrainingParticipation;
import pl.tfhub.api.training.entity.Training;
import pl.tfhub.api.training.entity.TrainingDefinition;
import pl.tfhub.api.training.entity.TrainingSegment;
import pl.tfhub.api.training.entity.TrainingSegmentExecution;
import pl.tfhub.api.training.enums.TrainingExecutionStatus;
import pl.tfhub.api.traininggroup.entity.TrainingGroup;
import pl.tfhub.api.traininggroup.repository.TrainingGroupRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Testcontainers
class TrainingDomainIntegrationTests {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TrainingGroupRepository trainingGroupRepository;

    @Autowired
    private AthleteRepository athleteRepository;

    @Autowired
    private TrainingRepository trainingRepository;

    @Autowired
    private TrainingDefinitionRepository definitionRepository;

    @Autowired
    private TrainingSegmentRepository segmentRepository;

    @Autowired
    private AthleteTrainingParticipationRepository participationRepository;

    @Autowired
    private TrainingSegmentExecutionRepository executionRepository;

    @BeforeEach
    void resetDatabase() {
        jdbcTemplate.execute("""
                TRUNCATE TABLE training_segment_executions, training_athlete_participations,
                    training_segments, training_definitions, training_sessions,
                    athletes, training_groups RESTART IDENTITY CASCADE
                """);
        jdbcTemplate.update("INSERT INTO training_groups (name) VALUES ('Group A')");
        jdbcTemplate.update("""
                INSERT INTO athletes (first_name, last_name, birth_year, gender, training_group_id)
                VALUES ('Alex', 'Runner', 2000, 'MALE', 1)
                """);
    }

    @Test
    void persistsTrainingHierarchyAndExecutionWithZeroResultAndDefaultSpikes() {
        TrainingGroup group = trainingGroupRepository.findById(1L).orElseThrow();
        Athlete athlete = athleteRepository.findById(1L).orElseThrow();

        Training training = trainingRepository.saveAndFlush(new Training("Intervals", LocalDate.now(), group));
        TrainingDefinition definition =
                definitionRepository.saveAndFlush(new TrainingDefinition(training, "Main set", 1));
        TrainingSegment segment = segmentRepository.saveAndFlush(new TrainingSegment(definition, 1, 400, 60));
        AthleteTrainingParticipation participation = participationRepository.saveAndFlush(
                new AthleteTrainingParticipation(training, definition, athlete));
        TrainingSegmentExecution execution = executionRepository.saveAndFlush(new TrainingSegmentExecution(
                participation, segment, definition, TrainingExecutionStatus.RECORDED, BigDecimal.ZERO, false));

        TrainingSegmentExecution persisted = executionRepository.findById(execution.getId()).orElseThrow();
        assertEquals(TrainingExecutionStatus.RECORDED, persisted.getStatus());
        assertEquals(BigDecimal.ZERO.setScale(3), persisted.getMeasuredResult());
        assertFalse(persisted.isUsesSpikes());
        assertTrue(trainingRepository.findById(training.getId()).orElseThrow().isActive());
        var storedRelations = jdbcTemplate.queryForMap("""
                SELECT p.training_session_id, p.training_definition_id, e.training_segment_id
                FROM training_segment_executions e
                JOIN training_athlete_participations p ON p.id = e.participation_id
                WHERE e.id = ?
                """, execution.getId());
        assertEquals(training.getId(), ((Number) storedRelations.get("training_session_id")).longValue());
        assertEquals(definition.getId(), ((Number) storedRelations.get("training_definition_id")).longValue());
        assertEquals(segment.getId(), ((Number) storedRelations.get("training_segment_id")).longValue());

        long secondSegmentId = insertSegment(definition.getId(), 2);
        Long executionId = jdbcTemplate.queryForObject("""
                INSERT INTO training_segment_executions
                    (participation_id, training_segment_id, training_definition_id, status)
                VALUES (?, ?, ?, 'PENDING')
                RETURNING id
                """, Long.class, participation.getId(), secondSegmentId, definition.getId());
        assertNotNull(executionId);
        assertFalse(jdbcTemplate.queryForObject(
                "SELECT uses_spikes FROM training_segment_executions WHERE id = ?",
                Boolean.class, executionId));
    }

    @Test
    void rejectsDuplicateAthleteParticipationAndCrossDefinitionRelationships() {
        TrainingRows rows = insertTrainingRows();
        long secondDefinitionId = insertDefinition(rows.trainingId(), "Second plan", 2);

        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update("""
                INSERT INTO training_athlete_participations
                    (training_session_id, training_definition_id, athlete_id)
                VALUES (?, ?, ?)
                """, rows.trainingId(), secondDefinitionId, rows.athleteId()));

        long secondTrainingId = insertTraining("Other session");
        long unrelatedDefinitionId = insertDefinition(secondTrainingId, "Other plan", 1);
        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update("""
                INSERT INTO training_athlete_participations
                    (training_session_id, training_definition_id, athlete_id)
                VALUES (?, ?, ?)
                """, rows.trainingId(), unrelatedDefinitionId, rows.athleteId()));
    }

    @Test
    void rejectsExecutionWithMismatchedSegmentOrInvalidStatusAndResultPair() {
        TrainingRows rows = insertTrainingRows();
        long secondDefinitionId = insertDefinition(rows.trainingId(), "Second plan", 2);
        long secondSegmentId = insertSegment(secondDefinitionId, 1);

        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update("""
                INSERT INTO training_segment_executions
                    (participation_id, training_segment_id, training_definition_id, status)
                VALUES (?, ?, ?, 'PENDING')
                """, rows.participationId(), secondSegmentId, rows.definitionId()));

        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update("""
                INSERT INTO training_segment_executions
                    (participation_id, training_segment_id, training_definition_id, status, measured_result)
                VALUES (?, ?, ?, 'PENDING', 1.250)
                """, rows.participationId(), rows.segmentId(), rows.definitionId()));

        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update("""
                INSERT INTO training_segment_executions
                    (participation_id, training_segment_id, training_definition_id, status)
                VALUES (?, ?, ?, 'RECORDED')
                """, rows.participationId(), rows.segmentId(), rows.definitionId()));

        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update("""
                INSERT INTO training_segment_executions
                    (participation_id, training_segment_id, training_definition_id, status, measured_result)
                VALUES (?, ?, ?, 'RECORDED', -0.001)
                """, rows.participationId(), rows.segmentId(), rows.definitionId()));
    }

    @Test
    void rejectsInvalidRequiredFieldsAndSegmentValues() {
        TrainingRows rows = insertTrainingRows();

        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update("""
                INSERT INTO training_sessions (training_date, training_group_id)
                VALUES (CURRENT_DATE, 1)
                """));
        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update("""
                INSERT INTO training_definitions (training_session_id, name, sequence_number)
                VALUES (?, 'Invalid sequence', 0)
                """, rows.trainingId()));
        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update("""
                INSERT INTO training_segments (training_definition_id, sequence_number, distance_m)
                VALUES (?, 1, 0)
                """, rows.definitionId()));
        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update("""
                INSERT INTO training_segment_executions
                    (participation_id, training_segment_id, training_definition_id, status)
                VALUES (?, ?, ?, 'UNKNOWN')
                """, rows.participationId(), rows.segmentId(), rows.definitionId()));
    }

    private TrainingRows insertTrainingRows() {
        long trainingId = insertTraining("Training");
        long definitionId = insertDefinition(trainingId, "Plan", 1);
        long segmentId = insertSegment(definitionId, 1);
        long participationId = jdbcTemplate.queryForObject("""
                INSERT INTO training_athlete_participations
                    (training_session_id, training_definition_id, athlete_id)
                VALUES (?, ?, 1)
                RETURNING id
                """, Long.class, trainingId, definitionId);
        return new TrainingRows(trainingId, definitionId, segmentId, participationId, 1L);
    }

    private long insertTraining(String name) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO training_sessions (name, training_date, training_group_id)
                VALUES (?, CURRENT_DATE, 1)
                RETURNING id
                """, Long.class, name);
    }

    private long insertDefinition(long trainingId, String name, int sequenceNumber) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO training_definitions (training_session_id, name, sequence_number)
                VALUES (?, ?, ?)
                RETURNING id
                """, Long.class, trainingId, name, sequenceNumber);
    }

    private long insertSegment(long definitionId, int sequenceNumber) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO training_segments
                    (training_definition_id, sequence_number, distance_m)
                VALUES (?, ?, 400)
                RETURNING id
                """, Long.class, definitionId, sequenceNumber);
    }

    private record TrainingRows(
            long trainingId,
            long definitionId,
            long segmentId,
            long participationId,
            long athleteId) {
    }
}

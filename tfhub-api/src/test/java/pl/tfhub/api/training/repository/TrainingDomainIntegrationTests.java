package pl.tfhub.api.training.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import pl.tfhub.api.athlete.entity.Athlete;
import pl.tfhub.api.athlete.enums.Gender;
import pl.tfhub.api.athlete.exception.AthleteNotFoundException;
import pl.tfhub.api.athlete.repository.AthleteRepository;
import pl.tfhub.api.training.dto.request.CreateTrainingDefinitionRequest;
import pl.tfhub.api.training.dto.request.CreateTrainingRequest;
import pl.tfhub.api.training.dto.request.CreateTrainingSegmentRequest;
import pl.tfhub.api.training.dto.request.EditTrainingDefinitionRequest;
import pl.tfhub.api.training.dto.request.EditTrainingExecutionRequest;
import pl.tfhub.api.training.dto.request.EditTrainingParticipationRequest;
import pl.tfhub.api.training.dto.request.EditTrainingRequest;
import pl.tfhub.api.training.dto.request.EditTrainingSegmentRequest;
import pl.tfhub.api.training.dto.response.TrainingDetailsResponse;
import pl.tfhub.api.training.service.TrainingDeactivationService;
import pl.tfhub.api.training.entity.AthleteTrainingParticipation;
import pl.tfhub.api.training.entity.Training;
import pl.tfhub.api.training.entity.TrainingDefinition;
import pl.tfhub.api.training.entity.TrainingSegment;
import pl.tfhub.api.training.entity.TrainingSegmentExecution;
import pl.tfhub.api.training.enums.TrainingExecutionStatus;
import pl.tfhub.api.training.exception.InactiveTrainingException;
import pl.tfhub.api.training.exception.InvalidTrainingRequestException;
import pl.tfhub.api.training.exception.TrainingNotFoundException;
import pl.tfhub.api.training.service.TrainingCreationService;
import pl.tfhub.api.training.service.TrainingDetailsService;
import pl.tfhub.api.training.service.TrainingQueryService;
import pl.tfhub.api.training.service.TrainingUpdateService;
import pl.tfhub.api.traininggroup.entity.TrainingGroup;
import pl.tfhub.api.traininggroup.exception.TrainingGroupNotFoundException;
import pl.tfhub.api.traininggroup.repository.TrainingGroupRepository;
import pl.tfhub.api.user.enums.UserRole;
import pl.tfhub.api.user.service.AuthenticatedUser;

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

    @Autowired
    private TrainingQueryService trainingQueryService;

    @Autowired
    private TrainingCreationService trainingCreationService;

    @Autowired
    private TrainingDetailsService trainingDetailsService;

    @Autowired
    private TrainingUpdateService trainingUpdateService;

    @Autowired
    private TrainingDeactivationService trainingDeactivationService;

    @BeforeEach
    void resetDatabase() {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        new AuthenticatedUser(1, "test-admin", "", UserRole.ADMIN, true), null, java.util.List.of()));
        jdbcTemplate.execute("""
                TRUNCATE TABLE training_segment_executions, training_athlete_participations,
                    training_segments, training_definitions, training_sessions,
                    athletes, training_groups, users RESTART IDENTITY CASCADE
                """);
        jdbcTemplate.update("INSERT INTO training_groups (name) VALUES ('Group A')");
        jdbcTemplate.update("""
                INSERT INTO athletes (first_name, last_name, birth_year, gender, training_group_id)
                VALUES ('Alex', 'Runner', 2000, 'MALE', 1), ('Sam', 'Runner', 2001, 'MALE', 1),
                    ('Taylor', 'Runner', 2002, 'MALE', 1)
                """);
    }

    @org.junit.jupiter.api.AfterEach
    void clearSecurityContext() {
                SecurityContextHolder.clearContext();
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

    @Test
    void adminGetsAllActiveTrainingsWithDefinitionsAndParticipantCountsInDateOrder() {
        long older = insertTraining("Older", 1, LocalDate.parse("2026-01-01"));
        long newerFirst = insertTraining("Newer first", 1, LocalDate.parse("2026-03-01"));
        long newerSecond = insertTraining("Newer second", 1, LocalDate.parse("2026-03-01"));
        long inactive = insertTraining("Inactive", 1, LocalDate.parse("2026-04-01"));
        jdbcTemplate.update("UPDATE training_sessions SET active = FALSE WHERE id = ?", inactive);

        long firstDefinition = insertDefinition(newerFirst, "Main", 1);
        insertDefinition(newerFirst, "Alternative", 2);
        long segmentId = insertSegment(firstDefinition, 1);
        long participationId = insertParticipation(newerFirst, firstDefinition, 1);
        insertParticipation(newerFirst, firstDefinition, 2);
        insertExecution(participationId, segmentId, firstDefinition, "PENDING");

        var trainings = trainingQueryService.listTrainings();
        assertEquals(java.util.List.of(newerSecond, newerFirst, older),
                trainings.stream().map(item -> item.id()).toList());
        var newer = trainings.get(1);
        assertEquals("Newer first", newer.name());
        assertEquals(LocalDate.parse("2026-03-01"), newer.trainingDate());
        assertEquals(1L, newer.trainingGroup().id());
        assertEquals("Group A", newer.trainingGroup().name());
        assertEquals(2L, newer.definitionCount());
        assertEquals(2L, newer.participantCount());
        assertEquals(0L, trainings.get(0).definitionCount());
        assertEquals(0L, trainings.get(0).participantCount());
    }

    @Test
    void coachOnlyGetsActiveTrainingsInAssignedGroupsAndCountsHistoricalParticipants() {
        long assignedGroup = insertGroup("Assigned group");
        long unassignedGroup = insertGroup("Unassigned group");
        long firstTraining = insertTraining("Assigned recent", assignedGroup, LocalDate.parse("2026-05-01"));
        long secondTraining = insertTraining("Assigned older", 1, LocalDate.parse("2026-02-01"));
        long hiddenTraining = insertTraining("Unassigned", unassignedGroup, LocalDate.parse("2026-06-01"));
        long inactiveTraining = insertTraining("Inactive", assignedGroup, LocalDate.parse("2026-07-01"));
        jdbcTemplate.update("UPDATE training_sessions SET active = FALSE WHERE id = ?", inactiveTraining);

        jdbcTemplate.update("""
                INSERT INTO users (username, password_hash, role) VALUES ('coach-one', 'hash', 'COACH')
                """);
        jdbcTemplate.update("""
                INSERT INTO user_training_group (user_id, training_group_id) VALUES (1, ?), (1, 1)
                """, assignedGroup);

        long definitionId = insertDefinition(firstTraining, "Plan", 1);
        long participationId = insertParticipation(firstTraining, definitionId, 1);
        insertParticipant(firstTraining, definitionId, 2);
        long segmentId = insertSegment(definitionId, 1);
        insertExecution(participationId, segmentId, definitionId, "PENDING");
        jdbcTemplate.update("UPDATE athletes SET training_group_id = ? WHERE id = 1", unassignedGroup);

        authenticate(1, UserRole.COACH, "coach-one");
        var trainings = trainingQueryService.listTrainings();

        assertEquals(java.util.List.of(firstTraining, secondTraining),
                trainings.stream().map(item -> item.id()).toList());
        assertEquals(2L, trainings.get(0).participantCount());
        assertEquals(1L, trainings.get(0).definitionCount());
        assertEquals(assignedGroup, trainings.get(0).trainingGroup().id());
    }

    @Test
    void rejectsAuthenticatedUsersWithoutCoachOrAdminRole() {
        authenticate(1, UserRole.ATHLETE, "athlete-one");

        assertThrows(AccessDeniedException.class, () -> trainingQueryService.listTrainings());
        assertThrows(AccessDeniedException.class, () ->
                trainingCreationService.createTraining(basicTrainingRequest(1, List.of())));
        assertThrows(AccessDeniedException.class, () ->
                trainingUpdateService.updateTraining(1, emptyEditRequest("Training", null)));
    }

    @Test
    void adminCreatesFullTrainingForAnyGroupWithPendingExecutionForEachPair() {
        long otherGroupId = insertGroup("Other group");
        jdbcTemplate.update("UPDATE athletes SET training_group_id = ? WHERE id IN (1, 2, 3)", otherGroupId);
        CreateTrainingRequest request = new CreateTrainingRequest(
                "Competition prep",
                LocalDate.parse("2026-11-10"),
                otherGroupId,
                List.of(
                        definitionRequest("Tempo", 1,
                                List.of(
                                        new CreateTrainingSegmentRequest(1, 400, 60),
                                        new CreateTrainingSegmentRequest(2, 400, null)),
                                List.of(1L, 2L)),
                        definitionRequest("Sprints", 2,
                                List.of(new CreateTrainingSegmentRequest(1, 100, 120)),
                                List.of(3L))));

        var response = trainingCreationService.createTraining(request);

        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM training_sessions WHERE id = ? AND training_group_id = ? AND active",
                Integer.class, response.id(), otherGroupId));
        assertEquals(2, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM training_definitions WHERE training_session_id = ?",
                Integer.class, response.id()));
        assertEquals(3, jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM training_segments s
                JOIN training_definitions d ON d.id = s.training_definition_id
                WHERE d.training_session_id = ?
                """, Integer.class, response.id()));
        assertEquals(3, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM training_athlete_participations WHERE training_session_id = ?",
                Integer.class, response.id()));
        assertEquals(5, jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM training_segment_executions e
                JOIN training_athlete_participations p ON p.id = e.participation_id
                WHERE p.training_session_id = ? AND e.status = 'PENDING' AND NOT e.uses_spikes
                """, Integer.class, response.id()));
    }

    @Test
    void coachCanCreateForAssignedGroupButNotUnassignedGroup() {
        jdbcTemplate.update("""
                INSERT INTO users (username, password_hash, role) VALUES ('coach-create', 'hash', 'COACH')
                """);
        jdbcTemplate.update("""
                INSERT INTO user_training_group (user_id, training_group_id) VALUES (1, 1)
                """);
        authenticate(1, UserRole.COACH, "coach-create");
        CreateTrainingRequest request = basicTrainingRequest(1, List.of());

        assertNotNull(trainingCreationService.createTraining(request).id());
        assertThrows(AccessDeniedException.class, () ->
                trainingCreationService.createTraining(basicTrainingRequest(insertGroup("Unassigned"), List.of())));
    }

    @Test
    void rejectsMissingGroupsDuplicateOrdersAndDuplicateAthleteAssignments() {
        CreateTrainingRequest valid = basicTrainingRequest(1, List.of());

        assertThrows(TrainingGroupNotFoundException.class, () ->
                trainingCreationService.createTraining(basicTrainingRequest(999L, List.of())));

        CreateTrainingRequest duplicateSegmentOrder = new CreateTrainingRequest(
                valid.name(), valid.trainingDate(), valid.trainingGroupId(),
                List.of(definitionRequest("Plan", 1, List.of(
                        new CreateTrainingSegmentRequest(1, 100, null),
                        new CreateTrainingSegmentRequest(1, 200, null)), List.of())));
        assertThrows(InvalidTrainingRequestException.class, () ->
                trainingCreationService.createTraining(duplicateSegmentOrder));

        CreateTrainingRequest duplicateDefinitionOrder = new CreateTrainingRequest(
                valid.name(), valid.trainingDate(), valid.trainingGroupId(),
                List.of(
                        definitionRequest("Plan A", 1,
                                List.of(new CreateTrainingSegmentRequest(1, 100, null)), List.of()),
                        definitionRequest("Plan B", 1,
                                List.of(new CreateTrainingSegmentRequest(1, 200, null)), List.of())));
        assertThrows(InvalidTrainingRequestException.class, () ->
                trainingCreationService.createTraining(duplicateDefinitionOrder));

        CreateTrainingRequest duplicateAthlete = new CreateTrainingRequest(
                valid.name(), valid.trainingDate(), valid.trainingGroupId(),
                List.of(
                        definitionRequest("Plan A", 1,
                                List.of(new CreateTrainingSegmentRequest(1, 100, null)), List.of(1L)),
                        definitionRequest("Plan B", 2,
                                List.of(new CreateTrainingSegmentRequest(1, 200, null)), List.of(1L))));
        assertThrows(InvalidTrainingRequestException.class, () ->
                trainingCreationService.createTraining(duplicateAthlete));

        assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM training_sessions", Integer.class));
    }

    @Test
    void rejectsAthletesOutsideSelectedGroupOrMissingAthletes() {
        long otherGroup = insertGroup("Other group");
        jdbcTemplate.update("UPDATE athletes SET training_group_id = ? WHERE id = 2", otherGroup);
        assertThrows(InvalidTrainingRequestException.class, () ->
                trainingCreationService.createTraining(basicTrainingRequest(1, List.of(1L, 2L))));
        jdbcTemplate.update("UPDATE athletes SET training_group_id = NULL WHERE id = 2");
        assertThrows(InvalidTrainingRequestException.class, () ->
                trainingCreationService.createTraining(basicTrainingRequest(1, List.of(2L))));
        assertThrows(AthleteNotFoundException.class, () ->
                trainingCreationService.createTraining(basicTrainingRequest(1, List.of(999L))));
        assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM training_sessions", Integer.class));
    }

    @Test
    void rollsBackAllTrainingRowsIfExecutionPersistenceFails() {
        jdbcTemplate.execute("""
                CREATE FUNCTION reject_training_execution_insert() RETURNS trigger
                LANGUAGE plpgsql AS $$
                BEGIN
                    RAISE EXCEPTION 'test execution insert failure';
                END;
                $$
                """);
        jdbcTemplate.execute("""
                CREATE TRIGGER reject_training_execution_insert
                BEFORE INSERT ON training_segment_executions
                FOR EACH ROW EXECUTE FUNCTION reject_training_execution_insert()
                """);
        try {
            assertThrows(RuntimeException.class, () -> trainingCreationService.createTraining(
                    basicTrainingRequest(1, List.of(1L))));
            assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM training_sessions", Integer.class));
            assertEquals(0, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM training_athlete_participations", Integer.class));
        } finally {
            jdbcTemplate.execute("DROP TRIGGER IF EXISTS reject_training_execution_insert ON training_segment_executions");
            jdbcTemplate.execute("DROP FUNCTION IF EXISTS reject_training_execution_insert()");
        }
    }

    @Test
    void adminEditsAndReordersTrainingWithoutReplacingExistingRows() {
        long trainingId = insertTraining("Before", 1, LocalDate.parse("2026-01-01"));
        long firstDefinitionId = insertDefinition(trainingId, "Main", 1);
        long secondDefinitionId = insertDefinition(trainingId, "Alternative", 2);
        long firstSegmentId = insertSegment(firstDefinitionId, 1);
        long recordedSegmentId = insertSegment(firstDefinitionId, 2);
        long otherSegmentId = insertSegment(secondDefinitionId, 1);
        long firstParticipationId = insertParticipation(trainingId, firstDefinitionId, 1);
        long secondParticipationId = insertParticipation(trainingId, secondDefinitionId, 2);
        insertExecution(firstParticipationId, firstSegmentId, firstDefinitionId, "PENDING");
        long recordedExecutionId = jdbcTemplate.queryForObject("""
                INSERT INTO training_segment_executions
                    (participation_id, training_segment_id, training_definition_id, status, measured_result)
                VALUES (?, ?, ?, 'RECORDED', 12.500)
                RETURNING id
                """, Long.class, firstParticipationId, recordedSegmentId, firstDefinitionId);
        insertExecution(secondParticipationId, otherSegmentId, secondDefinitionId, "PENDING");

        EditTrainingRequest request = editRequest(
                "Updated training",
                "2026-02-02",
                null,
                editDefinition(firstDefinitionId, "Main updated", 2,
                        List.of(
                                editSegment(firstSegmentId, 2, 400, 60),
                                editSegment(recordedSegmentId, 1, 400, null)),
                        List.of(editParticipation(firstParticipationId, 1,
                                List.of(new EditTrainingExecutionRequest(
                                        recordedExecutionId,
                                        TrainingExecutionStatus.RECORDED,
                                        new BigDecimal("13.250"),
                                        true))))),
                editDefinition(secondDefinitionId, "Alternative", 1,
                        List.of(editSegment(otherSegmentId, 1, 800, null)),
                        List.of(editParticipation(secondParticipationId, 2, List.of()))));

        assertEquals(trainingId, trainingUpdateService.updateTraining(trainingId, request).id());
        assertEquals(1, jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM training_sessions
                WHERE id = ? AND name = 'Updated training' AND training_date = DATE '2026-02-02'
                    AND training_group_id = 1
                """, Integer.class, trainingId));
        assertEquals(2, jdbcTemplate.queryForObject("""
                SELECT sequence_number FROM training_definitions WHERE id = ?
                """, Integer.class, firstDefinitionId));
        assertEquals(1, jdbcTemplate.queryForObject("""
                SELECT sequence_number FROM training_definitions WHERE id = ?
                """, Integer.class, secondDefinitionId));
        assertEquals(1, jdbcTemplate.queryForObject("""
                SELECT sequence_number FROM training_segments WHERE id = ?
                """, Integer.class, recordedSegmentId));
        assertEquals(2, jdbcTemplate.queryForObject("""
                SELECT sequence_number FROM training_segments WHERE id = ?
                """, Integer.class, firstSegmentId));
        assertEquals(1, jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM training_segment_executions
                WHERE id = ? AND participation_id = ? AND training_segment_id = ?
                    AND measured_result = 13.250 AND uses_spikes
                """, Integer.class, recordedExecutionId, firstParticipationId, recordedSegmentId));
    }

    @Test
    void coachCanEditAssignedTrainingButNotUnassignedOrInactiveTraining() {
        long assignedTraining = insertTraining("Assigned", 1, LocalDate.now());
        long unassignedGroupId = insertGroup("Other");
        long unassignedTraining = insertTraining("Unassigned", unassignedGroupId, LocalDate.now());
        long inactiveTraining = insertTraining("Inactive", 1, LocalDate.now());
        jdbcTemplate.update("UPDATE training_sessions SET active = FALSE WHERE id = ?", inactiveTraining);
        jdbcTemplate.update("""
                INSERT INTO users (username, password_hash, role) VALUES ('coach-edit', 'hash', 'COACH')
                """);
        jdbcTemplate.update("""
                INSERT INTO user_training_group (user_id, training_group_id) VALUES (1, 1)
                """);
        authenticate(1, UserRole.COACH, "coach-edit");

        assertEquals(assignedTraining, trainingUpdateService.updateTraining(
                assignedTraining, emptyEditRequest("Changed", null)).id());
        assertThrows(AccessDeniedException.class, () -> trainingUpdateService.updateTraining(
                unassignedTraining, emptyEditRequest("Changed", null)));
        assertThrows(InactiveTrainingException.class, () -> trainingUpdateService.updateTraining(
                inactiveTraining, emptyEditRequest("Changed", null)));
    }

    @Test
    void rejectsChangingTrainingGroupAndDoesNotRevalidateExistingAthleteMembership() {
        long trainingId = insertTraining("Training");
        long definitionId = insertDefinition(trainingId, "Plan", 1);
        long segmentId = insertSegment(definitionId, 1);
        long participationId = insertParticipation(trainingId, definitionId, 1);
        insertExecution(participationId, segmentId, definitionId, "PENDING");
        long differentGroupId = insertGroup("Moved athlete");
        jdbcTemplate.update("UPDATE athletes SET training_group_id = ? WHERE id = 1", differentGroupId);

        EditTrainingRequest existingParticipant = editRequest(
                "Training", "2026-11-10", null,
                editDefinition(definitionId, "Plan", 1,
                        List.of(editSegment(segmentId, 1, 400, null)),
                        List.of(editParticipation(participationId, 1, List.of()))));
        trainingUpdateService.updateTraining(trainingId, existingParticipant);

        assertThrows(InvalidTrainingRequestException.class, () -> trainingUpdateService.updateTraining(
                trainingId, emptyEditRequest("Changed", 2L)));
        jdbcTemplate.update("UPDATE athletes SET training_group_id = ? WHERE id = 2", differentGroupId);
        EditTrainingRequest addingMovedAthlete = editRequest(
                "Training", "2026-11-10", null,
                editDefinition(definitionId, "Plan", 1,
                        List.of(editSegment(segmentId, 1, 400, null)),
                        List.of(
                                editParticipation(participationId, 1, List.of()),
                                editParticipation(null, 2, List.of()))));
        assertThrows(InvalidTrainingRequestException.class, () ->
                trainingUpdateService.updateTraining(trainingId, addingMovedAthlete));
    }

    @Test
    void preventsRemovingOrChangingDistanceOfSegmentWithRecordedExecution() {
        long trainingId = insertTraining("Training");
        long definitionId = insertDefinition(trainingId, "Plan", 1);
        long recordedSegmentId = insertSegment(definitionId, 1);
        long otherSegmentId = insertSegment(definitionId, 2);
        long participationId = insertParticipation(trainingId, definitionId, 1);
        jdbcTemplate.update("""
                INSERT INTO training_segment_executions
                    (participation_id, training_segment_id, training_definition_id, status, measured_result)
                VALUES (?, ?, ?, 'RECORDED', 10)
                """, participationId, recordedSegmentId, definitionId);
        insertExecution(participationId, otherSegmentId, definitionId, "PENDING");

        EditTrainingRequest removingRecorded = editRequest(
                "Training", "2026-11-10", null,
                editDefinition(definitionId, "Plan", 1,
                        List.of(editSegment(otherSegmentId, 1, 400, null)),
                        List.of(editParticipation(participationId, 1, List.of()))));
        EditTrainingRequest changingRecordedDistance = editRequest(
                "Training", "2026-11-10", null,
                editDefinition(definitionId, "Plan", 1,
                        List.of(
                                editSegment(recordedSegmentId, 1, 200, null),
                                editSegment(otherSegmentId, 2, 400, null)),
                        List.of(editParticipation(participationId, 1, List.of()))));

        assertThrows(InvalidTrainingRequestException.class, () ->
                trainingUpdateService.updateTraining(trainingId, removingRecorded));
        assertThrows(InvalidTrainingRequestException.class, () ->
                trainingUpdateService.updateTraining(trainingId, changingRecordedDistance));
        assertEquals(400, jdbcTemplate.queryForObject(
                "SELECT distance_m FROM training_segments WHERE id = ?", Integer.class, recordedSegmentId));
    }

    @Test
    void removesUnrecordedRowsAndCreatesPendingExecutionsForNewParticipantAndSegment() {
        long trainingId = insertTraining("Training");
        long definitionId = insertDefinition(trainingId, "Plan", 1);
        long existingSegmentId = insertSegment(definitionId, 1);
        long removedSegmentId = insertSegment(definitionId, 2);
        long existingParticipationId = insertParticipation(trainingId, definitionId, 1);
        long removedParticipationId = insertParticipation(trainingId, definitionId, 2);
        insertExecution(existingParticipationId, existingSegmentId, definitionId, "PENDING");
        insertExecution(existingParticipationId, removedSegmentId, definitionId, "PENDING");
        insertExecution(removedParticipationId, existingSegmentId, definitionId, "PENDING");
        insertExecution(removedParticipationId, removedSegmentId, definitionId, "PENDING");

        EditTrainingRequest request = editRequest(
                "Training", "2026-11-10", null,
                editDefinition(definitionId, "Plan", 1,
                        List.of(
                                editSegment(existingSegmentId, 1, 400, null),
                                editSegment(null, 2, 800, 30)),
                        List.of(
                                editParticipation(existingParticipationId, 1, List.of()),
                                editParticipation(null, 3, List.of()))));
        trainingUpdateService.updateTraining(trainingId, request);

        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM training_segments WHERE id = ?", Integer.class, removedSegmentId));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM training_athlete_participations WHERE id = ?",
                Integer.class, removedParticipationId));
        assertEquals(4, jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM training_segment_executions e
                JOIN training_athlete_participations p ON p.id = e.participation_id
                WHERE p.training_session_id = ? AND e.status = 'PENDING' AND NOT e.uses_spikes
                """, Integer.class, trainingId));
    }

    @Test
    void addsAndRemovesDefinitionsWithoutRecordedExecutionData() {
        long trainingId = insertTraining("Training");
        long retainedDefinitionId = insertDefinition(trainingId, "Old plan", 1);
        insertSegment(retainedDefinitionId, 1);
        long removedDefinitionId = insertDefinition(trainingId, "Unused plan", 2);
        insertSegment(removedDefinitionId, 1);

        EditTrainingRequest request = editRequest(
                "Training", "2026-11-10", null,
                editDefinition(retainedDefinitionId, "Renamed plan", 1,
                        List.of(editSegment(
                                jdbcTemplate.queryForObject("""
                                        SELECT id FROM training_segments
                                        WHERE training_definition_id = ?
                                        """, Long.class, retainedDefinitionId),
                                1, 400, null)),
                        List.of()),
                editDefinition(null, "New plan", 2,
                        List.of(editSegment(null, 1, 800, null)), List.of()));

        trainingUpdateService.updateTraining(trainingId, request);

        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM training_definitions WHERE id = ?",
                Integer.class, removedDefinitionId));
        assertEquals(1, jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM training_definitions
                WHERE training_session_id = ? AND name = 'Renamed plan' AND sequence_number = 1
                """, Integer.class, trainingId));
        assertEquals(1, jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM training_definitions
                WHERE training_session_id = ? AND name = 'New plan' AND sequence_number = 2
                """, Integer.class, trainingId));
    }

    @Test
    void rejectsInvalidExecutionStateAndRollsBackFailedPersistence() {
        long trainingId = insertTraining("Before");
        long definitionId = insertDefinition(trainingId, "Plan", 1);
        long segmentId = insertSegment(definitionId, 1);
        long participationId = insertParticipation(trainingId, definitionId, 1);
        long executionId = jdbcTemplate.queryForObject("""
                INSERT INTO training_segment_executions
                    (participation_id, training_segment_id, training_definition_id, status)
                VALUES (?, ?, ?, 'PENDING')
                RETURNING id
                """, Long.class, participationId, segmentId, definitionId);
        EditTrainingRequest invalidExecution = editRequest(
                "Changed", "2026-11-10", null,
                editDefinition(definitionId, "Plan", 1,
                        List.of(editSegment(segmentId, 1, 400, null)),
                        List.of(editParticipation(participationId, 1,
                                List.of(new EditTrainingExecutionRequest(
                                        executionId, TrainingExecutionStatus.PENDING,
                                        BigDecimal.ONE, false))))));
        assertThrows(InvalidTrainingRequestException.class, () ->
                trainingUpdateService.updateTraining(trainingId, invalidExecution));

        jdbcTemplate.execute("""
                CREATE FUNCTION reject_training_update_execution_insert() RETURNS trigger
                LANGUAGE plpgsql AS $$
                BEGIN
                    RAISE EXCEPTION 'test execution insert failure';
                END;
                $$
                """);
        jdbcTemplate.execute("""
                CREATE TRIGGER reject_training_update_execution_insert
                BEFORE INSERT ON training_segment_executions
                FOR EACH ROW EXECUTE FUNCTION reject_training_update_execution_insert()
                """);
        try {
            EditTrainingRequest addSegment = editRequest(
                    "Should roll back", "2026-11-10", null,
                    editDefinition(definitionId, "Plan", 1,
                            List.of(
                                    editSegment(segmentId, 1, 400, null),
                                    editSegment(null, 2, 800, null)),
                            List.of(editParticipation(participationId, 1, List.of()))));
            assertThrows(RuntimeException.class, () ->
                    trainingUpdateService.updateTraining(trainingId, addSegment));
            assertEquals("Before", jdbcTemplate.queryForObject(
                    "SELECT name FROM training_sessions WHERE id = ?", String.class, trainingId));
            assertEquals(1, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM training_segments WHERE training_definition_id = ?",
                    Integer.class, definitionId));
        } finally {
            jdbcTemplate.execute("DROP TRIGGER IF EXISTS reject_training_update_execution_insert "
                    + "ON training_segment_executions");
            jdbcTemplate.execute("DROP FUNCTION IF EXISTS reject_training_update_execution_insert()");
        }
    }

    @Test
    void rejectsRemovalOfDefinitionOrParticipationWithRecordedExecution() {
        long trainingId = insertTraining("Training");
        long definitionId = insertDefinition(trainingId, "Plan", 1);
        long segmentId = insertSegment(definitionId, 1);
        long participationId = insertParticipation(trainingId, definitionId, 1);
        jdbcTemplate.update("""
                INSERT INTO training_segment_executions
                    (participation_id, training_segment_id, training_definition_id, status, measured_result)
                VALUES (?, ?, ?, 'RECORDED', 10)
                """, participationId, segmentId, definitionId);

        assertThrows(InvalidTrainingRequestException.class, () ->
                trainingUpdateService.updateTraining(trainingId, emptyEditRequest("Training", null)));
        EditTrainingRequest removingParticipant = editRequest(
                "Training", "2026-11-10", null,
                editDefinition(definitionId, "Plan", 1,
                        List.of(editSegment(segmentId, 1, 400, null)), List.of()));
        assertThrows(InvalidTrainingRequestException.class, () ->
                trainingUpdateService.updateTraining(trainingId, removingParticipant));
    }

    @Test
    void adminViewsActiveAndInactiveTrainingWithOrderedCompleteHistory() {
        long activeId = insertTraining("Active", 1, LocalDate.parse("2026-03-01"));
        long inactiveId = insertTraining("Inactive", 1, LocalDate.parse("2026-04-01"));
        jdbcTemplate.update("UPDATE training_sessions SET active = FALSE WHERE id = ?", inactiveId);
        long secondDefinitionId = insertDefinition(activeId, "Second", 2);
        long firstDefinitionId = insertDefinition(activeId, "First", 1);
        long firstSegmentId = insertSegment(firstDefinitionId, 1);
        long secondSegmentId = insertSegment(firstDefinitionId, 2);
        long anotherDefinitionSegmentId = insertSegment(secondDefinitionId, 1);
        long aliceId = jdbcTemplate.queryForObject("""
                INSERT INTO athletes (first_name, last_name, birth_year, gender, training_group_id)
                VALUES ('Alice', 'Zulu', 2000, 'FEMALE', 1) RETURNING id
                """, Long.class);
        long bobId = jdbcTemplate.queryForObject("""
                INSERT INTO athletes (first_name, last_name, birth_year, gender, training_group_id)
                VALUES ('Bob', 'Alpha', 2000, 'MALE', 1) RETURNING id
                """, Long.class);
        long aliceParticipationId = insertParticipation(activeId, firstDefinitionId, aliceId);
        long bobParticipationId = insertParticipation(activeId, firstDefinitionId, bobId);
        insertExecution(aliceParticipationId, firstSegmentId, firstDefinitionId, "PENDING");
        long recordedExecutionId = jdbcTemplate.queryForObject("""
                INSERT INTO training_segment_executions
                    (participation_id, training_segment_id, training_definition_id,
                        status, measured_result, uses_spikes)
                VALUES (?, ?, ?, 'RECORDED', 12.340, TRUE) RETURNING id
                """, Long.class, aliceParticipationId, secondSegmentId, firstDefinitionId);
        insertExecution(bobParticipationId, firstSegmentId, firstDefinitionId, "COMPLETED_WITHOUT_RESULT");
        insertExecution(bobParticipationId, secondSegmentId, firstDefinitionId, "NOT_COMPLETED");
        long secondDefinitionParticipation = insertParticipation(activeId, secondDefinitionId, 3);
        insertExecution(secondDefinitionParticipation, anotherDefinitionSegmentId, secondDefinitionId, "PENDING");
        jdbcTemplate.update("UPDATE athletes SET training_group_id = NULL WHERE id = ?", aliceId);

        TrainingDetailsResponse details = trainingDetailsService.getTraining(activeId);
        assertEquals(activeId, details.id());
        assertTrue(details.active());
        assertEquals("Group A", details.trainingGroup().name());
        assertEquals(List.of(1, 2), details.definitions().stream()
                .map(definition -> definition.sequenceNumber()).toList());
        var firstDefinition = details.definitions().getFirst();
        assertEquals(List.of(1, 2), firstDefinition.segments().stream()
                .map(segment -> segment.sequenceNumber()).toList());
        assertEquals(List.of("Alpha", "Zulu"), firstDefinition.participations().stream()
                .map(participation -> participation.lastName()).toList());
        var alice = firstDefinition.participations().get(1);
        assertEquals(aliceId, alice.athleteId());
        assertEquals(List.of(1, 2), alice.executions().stream()
                .map(execution -> execution.segmentSequenceNumber()).toList());
        assertEquals(
                List.of(TrainingExecutionStatus.PENDING, TrainingExecutionStatus.RECORDED),
                alice.executions().stream().map(execution -> execution.status()).toList());
        assertEquals(recordedExecutionId, alice.executions().get(1).id());
        assertEquals(new BigDecimal("12.340"), alice.executions().get(1).measuredResult());
        assertTrue(alice.executions().get(1).usesSpikes());

        TrainingDetailsResponse inactiveDetails = trainingDetailsService.getTraining(inactiveId);
        assertFalse(inactiveDetails.active());
        assertTrue(inactiveDetails.definitions().isEmpty());
    }

    @Test
    void coachCanViewAssignedTrainingAndGetsNotFoundForUnassignedOrUnassignedLater() {
        long assignedTrainingId = insertTraining("Assigned");
        long otherGroupId = insertGroup("Other group");
        long hiddenTrainingId = insertTraining("Hidden", otherGroupId, LocalDate.now());
        jdbcTemplate.update("""
                INSERT INTO users (username, password_hash, role) VALUES ('coach-view', 'hash', 'COACH')
                """);
        jdbcTemplate.update("""
                INSERT INTO user_training_group (user_id, training_group_id) VALUES (1, 1)
                """);
        authenticate(1, UserRole.COACH, "coach-view");

        assertEquals(assignedTrainingId, trainingDetailsService.getTraining(assignedTrainingId).id());
        assertThrows(TrainingNotFoundException.class, () ->
                trainingDetailsService.getTraining(hiddenTrainingId));

        jdbcTemplate.update("DELETE FROM user_training_group WHERE user_id = 1 AND training_group_id = 1");
        assertThrows(TrainingNotFoundException.class, () ->
                trainingDetailsService.getTraining(assignedTrainingId));
    }

    @Test
    void rejectsMissingTrainingAndUsersWithoutCoachOrAdminRole() {
        assertThrows(TrainingNotFoundException.class, () -> trainingDetailsService.getTraining(999));
        authenticate(1, UserRole.ATHLETE, "athlete-view");
        assertThrows(AccessDeniedException.class, () -> trainingDetailsService.getTraining(1));
    }

    @Test
    void adminDeactivatesTrainingPreservingRelatedDataAndVisibilityRules() {
        long trainingId = insertTraining("Training");
        long definitionId = insertDefinition(trainingId, "Plan", 1);
        long segmentId = insertSegment(definitionId, 1);
        long participationId = insertParticipation(trainingId, definitionId, 1);
        jdbcTemplate.update("""
                INSERT INTO training_segment_executions
                    (participation_id, training_segment_id, training_definition_id,
                        status, measured_result, uses_spikes)
                VALUES (?, ?, ?, 'RECORDED', 12.345, TRUE)
                """, participationId, segmentId, definitionId);

        assertEquals(trainingId, trainingDeactivationService.deactivateTraining(trainingId).id());
        assertEquals(1, jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM training_sessions WHERE id = ? AND NOT active
                """, Integer.class, trainingId));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM training_definitions WHERE training_session_id = ?",
                Integer.class, trainingId));
        assertEquals(1, jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM training_segments s
                JOIN training_definitions d ON d.id = s.training_definition_id
                WHERE d.training_session_id = ?
                """, Integer.class, trainingId));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM training_athlete_participations WHERE training_session_id = ?",
                Integer.class, trainingId));
        assertEquals(1, jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM training_segment_executions
                WHERE participation_id = ? AND status = 'RECORDED'
                    AND measured_result = 12.345 AND uses_spikes
                """, Integer.class, participationId));
        assertTrue(trainingQueryService.listTrainings().stream().noneMatch(item -> item.id() == trainingId));
        assertFalse(trainingDetailsService.getTraining(trainingId).active());
        assertThrows(InactiveTrainingException.class, () ->
                trainingUpdateService.updateTraining(trainingId, emptyEditRequest("Changed", null)));
        assertThrows(InactiveTrainingException.class, () ->
                trainingDeactivationService.deactivateTraining(trainingId));
    }

    @Test
    void coachCanDeactivateAssignedTrainingButCannotDeactivateUnassignedTraining() {
        long assignedTraining = insertTraining("Assigned");
        long otherGroupId = insertGroup("Other group");
        long unassignedTraining = insertTraining("Unassigned", otherGroupId, LocalDate.now());
        jdbcTemplate.update("""
                INSERT INTO users (username, password_hash, role) VALUES ('coach-deactivate', 'hash', 'COACH')
                """);
        jdbcTemplate.update("""
                INSERT INTO user_training_group (user_id, training_group_id) VALUES (1, 1)
                """);
        authenticate(1, UserRole.COACH, "coach-deactivate");

        assertEquals(assignedTraining, trainingDeactivationService.deactivateTraining(assignedTraining).id());
        assertThrows(TrainingNotFoundException.class, () ->
                trainingDeactivationService.deactivateTraining(unassignedTraining));
    }

    @Test
    void rejectsDeactivationByUnauthorizedUserAndForMissingTraining() {
        assertThrows(TrainingNotFoundException.class, () ->
                trainingDeactivationService.deactivateTraining(999));
        authenticate(1, UserRole.ATHLETE, "athlete-deactivate");
        assertThrows(AccessDeniedException.class, () ->
                trainingDeactivationService.deactivateTraining(1));
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
        return insertTraining(name, 1, LocalDate.now());
    }

    private long insertTraining(String name, long groupId, LocalDate trainingDate) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO training_sessions (name, training_date, training_group_id)
                VALUES (?, ?, ?)
                RETURNING id
                """, Long.class, name, trainingDate, groupId);
    }

    private long insertGroup(String name) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO training_groups (name) VALUES (?) RETURNING id
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

    private long insertParticipation(long trainingId, long definitionId, long athleteId) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO training_athlete_participations
                    (training_session_id, training_definition_id, athlete_id)
                VALUES (?, ?, ?)
                RETURNING id
                """, Long.class, trainingId, definitionId, athleteId);
    }

    private void insertParticipant(long trainingId, long definitionId, long athleteId) {
        insertParticipation(trainingId, definitionId, athleteId);
    }

    private void insertExecution(long participationId, long segmentId, long definitionId, String status) {
        jdbcTemplate.update("""
                INSERT INTO training_segment_executions
                    (participation_id, training_segment_id, training_definition_id, status)
                VALUES (?, ?, ?, ?)
                """, participationId, segmentId, definitionId, status);
    }

    private void authenticate(long userId, UserRole role, String username) {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        new AuthenticatedUser(userId, username, "", role, true), null, java.util.List.of()));
    }

    private CreateTrainingRequest basicTrainingRequest(long groupId, List<Long> athleteIds) {
        return new CreateTrainingRequest("Training", LocalDate.parse("2026-11-10"), groupId,
                List.of(definitionRequest("Plan", 1,
                        List.of(new CreateTrainingSegmentRequest(1, 400, null)), athleteIds)));
    }

    private EditTrainingRequest emptyEditRequest(String name, Long groupId) {
        return editRequest(name, "2026-11-10", groupId,
                editDefinition(null, "Plan", 1,
                        List.of(editSegment(null, 1, 400, null)), List.of()));
    }

    private EditTrainingRequest editRequest(
            String name, String date, Long groupId, EditTrainingDefinitionRequest... definitions) {
        return new EditTrainingRequest(name, LocalDate.parse(date), groupId, List.of(definitions));
    }

    private EditTrainingDefinitionRequest editDefinition(
            Long id,
            String name,
            int sequenceNumber,
            List<EditTrainingSegmentRequest> segments,
            List<EditTrainingParticipationRequest> participations) {
        return new EditTrainingDefinitionRequest(id, name, sequenceNumber, segments, participations);
    }

    private EditTrainingSegmentRequest editSegment(
            Long id, int sequenceNumber, int distanceM, Integer restAfterSeconds) {
        return new EditTrainingSegmentRequest(id, sequenceNumber, distanceM, restAfterSeconds);
    }

    private EditTrainingParticipationRequest editParticipation(
            Long id, long athleteId, List<EditTrainingExecutionRequest> executionUpdates) {
        return new EditTrainingParticipationRequest(id, athleteId, executionUpdates);
    }

    private CreateTrainingDefinitionRequest definitionRequest(
            String name,
            int sequenceNumber,
            List<CreateTrainingSegmentRequest> segments,
            List<Long> athleteIds) {
        return new CreateTrainingDefinitionRequest(name, sequenceNumber, segments, athleteIds);
    }

    private record TrainingRows(
            long trainingId,
            long definitionId,
            long segmentId,
            long participationId,
            long athleteId) {
    }
}

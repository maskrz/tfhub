package pl.tfhub.api.training.repository;

import java.math.BigDecimal;
import java.sql.DriverManager;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.test.web.servlet.MockMvc;
import jakarta.servlet.http.Cookie;
import pl.tfhub.api.athlete.entity.Athlete;
import pl.tfhub.api.athlete.enums.Gender;
import pl.tfhub.api.athlete.exception.AthleteNotFoundException;
import pl.tfhub.api.athlete.repository.AthleteRepository;
import pl.tfhub.api.training.dto.request.CreateTrainingDefinitionRequest;
import pl.tfhub.api.training.dto.request.CreateFreeTextTrainingRequest;
import pl.tfhub.api.training.dto.request.CreateTrainingRequest;
import pl.tfhub.api.training.dto.request.CreateTrainingSegmentRequest;
import pl.tfhub.api.training.dto.request.CreateTrainingSectionRequest;
import pl.tfhub.api.training.dto.request.EditTrainingDefinitionRequest;
import pl.tfhub.api.training.dto.request.EditTrainingExecutionRequest;
import pl.tfhub.api.training.dto.request.EditTrainingParticipationRequest;
import pl.tfhub.api.training.dto.request.EditTrainingRequest;
import pl.tfhub.api.training.dto.request.EditTrainingSegmentRequest;
import pl.tfhub.api.training.dto.request.EditFreeTextTrainingRequest;
import pl.tfhub.api.training.dto.request.EditTrainingSectionRequest;
import pl.tfhub.api.training.dto.request.AddTrainingParticipationRequest;
import pl.tfhub.api.training.dto.request.CompleteTrainingRequest;
import pl.tfhub.api.training.dto.request.NewTrainingExecutionRequest;
import pl.tfhub.api.training.dto.request.UpdateTrainingParticipationRequest;
import pl.tfhub.api.training.dto.response.TrainingDetailsResponse;
import pl.tfhub.api.training.service.TrainingDeletionService;
import pl.tfhub.api.training.service.FreeTextTrainingService;
import pl.tfhub.api.training.entity.AthleteTrainingParticipation;
import pl.tfhub.api.training.entity.Training;
import pl.tfhub.api.training.entity.TrainingDefinition;
import pl.tfhub.api.training.entity.TrainingSection;
import pl.tfhub.api.training.entity.TrainingSegment;
import pl.tfhub.api.training.entity.TrainingSegmentExecution;
import pl.tfhub.api.training.enums.TrainingExecutionStatus;
import pl.tfhub.api.training.enums.TrainingAssignmentType;
import pl.tfhub.api.training.enums.TrainingStatus;
import pl.tfhub.api.training.enums.TrainingType;
import pl.tfhub.api.training.exception.InvalidTrainingRequestException;
import pl.tfhub.api.training.exception.TrainingNotFoundException;
import pl.tfhub.api.training.service.TrainingCreationService;
import pl.tfhub.api.training.service.TrainingCompletionService;
import pl.tfhub.api.training.service.TrainingDetailsService;
import pl.tfhub.api.training.service.TrainingQueryService;
import pl.tfhub.api.training.service.TrainingUpdateService;
import pl.tfhub.api.training.service.AthleteTrainingHistoryService;
import pl.tfhub.api.traininggroup.entity.TrainingGroup;
import pl.tfhub.api.traininggroup.exception.TrainingGroupNotFoundException;
import pl.tfhub.api.traininggroup.repository.TrainingGroupRepository;
import pl.tfhub.api.training.repository.TrainingSectionRepository;
import pl.tfhub.api.user.enums.UserRole;
import pl.tfhub.api.user.service.AuthenticatedUser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
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
    private TrainingCompletionService trainingCompletionService;

    @Autowired
    private TrainingDetailsService trainingDetailsService;

    @Autowired
    private TrainingUpdateService trainingUpdateService;

    @Autowired
    private TrainingDeletionService trainingDeletionService;

    @Autowired
    private FreeTextTrainingService freeTextTrainingService;

    @Autowired
    private TrainingSectionRepository sectionRepository;

    @Autowired
    private AthleteTrainingHistoryService athleteTrainingHistoryService;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void resetDatabase() {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        new AuthenticatedUser(1, "test-admin", "", UserRole.ADMIN, true), null, java.util.List.of()));
        jdbcTemplate.execute("""
                TRUNCATE TABLE training_segment_executions, training_athlete_participations, training_sections,
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
        assertEquals(TrainingStatus.PLANNED, trainingRepository.findById(training.getId()).orElseThrow().getStatus());
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
    void adminGetsPlannedTrainingsByDefaultAndCanIncludeCompletedTrainings() {
        long older = insertTraining("Older", 1, LocalDate.parse("2026-01-01"));
        long newerFirst = insertTraining("Newer first", 1, LocalDate.parse("2026-03-01"));
        long newerSecond = insertTraining("Newer second", 1, LocalDate.parse("2026-03-01"));
        long completed = insertTraining("Completed", 1, LocalDate.parse("2026-04-01"));
        jdbcTemplate.update("UPDATE training_sessions SET status = 'COMPLETED' WHERE id = ?", completed);

        long firstDefinition = insertDefinition(newerFirst, "Main", 1);
        insertDefinition(newerFirst, "Alternative", 2);
        long segmentId = insertSegment(firstDefinition, 1);
        long participationId = insertParticipation(newerFirst, firstDefinition, 1);
        insertParticipation(newerFirst, firstDefinition, 2);
        insertExecution(participationId, segmentId, firstDefinition, "PENDING");

        var trainings = trainingQueryService.listTrainings(false, null);
        assertEquals(java.util.List.of(newerSecond, newerFirst, older),
                trainings.stream().map(item -> item.id()).toList());
        var newer = trainings.get(1);
        assertEquals("Newer first", newer.name());
        assertEquals(LocalDate.parse("2026-03-01"), newer.trainingDate());
        assertEquals(1L, newer.trainingGroup().id());
        assertEquals("Group A", newer.trainingGroup().name());
        assertEquals(2L, newer.definitionCount());
        assertEquals(2L, newer.participantCount());
        assertEquals(TrainingStatus.PLANNED, newer.status());
        assertEquals(TrainingType.TEMPO, newer.trainingType());
        assertEquals(0L, trainings.get(0).definitionCount());
        assertEquals(0L, trainings.get(0).participantCount());
        var allTrainings = trainingQueryService.listTrainings(true, null);
        assertEquals(java.util.List.of(completed, newerSecond, newerFirst, older),
                allTrainings.stream().map(item -> item.id()).toList());
        assertEquals(TrainingStatus.COMPLETED, allTrainings.get(0).status());
    }

    @Test
    void filtersByTypeAndDefaultsPersistedRowsToTempo() {
        long tempoTraining = insertTraining("Tempo", 1, LocalDate.parse("2026-03-01"));
        long freeTextTraining = insertTraining("General", 1, LocalDate.parse("2026-04-01"));
        jdbcTemplate.update(
                "UPDATE training_sessions SET training_type = 'FREE_TEXT' WHERE id = ?", freeTextTraining);

        assertEquals(List.of(freeTextTraining), trainingQueryService.listTrainings(true, TrainingType.FREE_TEXT)
                .stream().map(item -> item.id()).toList());
        assertEquals(List.of(tempoTraining), trainingQueryService.listTrainings(true, TrainingType.TEMPO)
                .stream().map(item -> item.id()).toList());
        assertEquals(TrainingType.TEMPO, trainingRepository.findById(tempoTraining).orElseThrow().getTrainingType());
        assertEquals(List.of(freeTextTraining, tempoTraining), trainingQueryService.listTrainings(true, null)
                .stream().map(item -> item.id()).toList());
    }

    @Test
    void equipmentIsPersistedAndNeverSerializedToAthletesOrLists() throws Exception {
        String equipment = "Medicine ball\nBands";
        long groupTraining = freeTextTrainingService.create(new CreateFreeTextTrainingRequest(
                "Group", null, TrainingAssignmentType.GROUP, 1L, List.of(),
                List.of(new CreateTrainingSectionRequest("Warm-up", "Run", 1)), equipment)).id();
        long individualTraining = freeTextTrainingService.create(new CreateFreeTextTrainingRequest(
                "Individual", null, TrainingAssignmentType.ATHLETES, null, List.of(1L),
                List.of(new CreateTrainingSectionRequest("Warm-up", "Run", 1)), equipment)).id();
        assertEquals(equipment, trainingDetailsService.getTraining(groupTraining).equipment());
        var section = sectionRepository.findAllByTraining_IdOrderBySequenceNumberAsc(groupTraining).getFirst();
        var editSections = List.of(new EditTrainingSectionRequest(section.getId(), "Warm-up", "Run", 1));
        freeTextTrainingService.update(groupTraining, new EditFreeTextTrainingRequest(
                "Group", null, TrainingAssignmentType.GROUP, 1L, List.of(), editSections));
        assertEquals(equipment, trainingDetailsService.getTraining(groupTraining).equipment());
        var json = new tools.jackson.databind.ObjectMapper();
        freeTextTrainingService.update(groupTraining, new EditFreeTextTrainingRequest(
                "Group", null, TrainingAssignmentType.GROUP, 1L, List.of(), editSections, json.readTree("null")));
        assertNullEquipment(groupTraining);
        jdbcTemplate.update("UPDATE training_sessions SET equipment = ? WHERE id = ?", equipment, groupTraining);
        long tempo = insertTraining("Tempo");
        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update(
                "UPDATE training_sessions SET equipment = ? WHERE id = ?", equipment, tempo));

        String password = "equipment-test-password";
        for (String role : List.of("ADMIN", "COACH", "ATHLETE")) {
            jdbcTemplate.update("INSERT INTO users (username, password_hash, role) VALUES (?, ?, ?)",
                    role.toLowerCase(), passwordEncoder.encode(password), role);
        }
        jdbcTemplate.update("INSERT INTO user_training_group (user_id, training_group_id) VALUES (2, 1)");
        jdbcTemplate.update("INSERT INTO user_athlete_access (user_id, athlete_id) VALUES (3, 1)");
        SecurityContextHolder.clearContext();
        MockHttpSession admin = loginForHistory("admin", password);
        MockHttpSession coach = loginForHistory("coach", password);
        MockHttpSession athlete = loginForHistory("athlete", password);
        for (long id : List.of(groupTraining, individualTraining)) {
            mockMvc.perform(get("/api/trainings/" + id).session(admin))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.equipment").value(equipment));
            mockMvc.perform(get("/api/trainings/" + id).session(coach))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.equipment").value(equipment));
            mockMvc.perform(get("/api/trainings/" + id).session(athlete))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.equipment").doesNotHaveJsonPath());
        }
        mockMvc.perform(get("/api/trainings").session(athlete))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].equipment").doesNotHaveJsonPath());
        mockMvc.perform(get("/api/training-imports").session(athlete)).andExpect(status().isForbidden());
    }

    private void assertNullEquipment(long trainingId) {
        org.junit.jupiter.api.Assertions.assertNull(trainingDetailsService.getTraining(trainingId).equipment());
    }

    @Test
    void managesFreeTextSectionsAndCompletesWithoutAthleteParticipation() {
        var created = freeTextTrainingService.create(new CreateFreeTextTrainingRequest(
                "General session",
                LocalDate.parse("2026-03-02"),
                TrainingAssignmentType.GROUP,
                1L,
                List.of(),
                List.of(
                        new CreateTrainingSectionRequest("Warm-up", "Easy run", 1),
                        new CreateTrainingSectionRequest("Main part", "Running drills", 2))));

        Training training = trainingRepository.findById(created.id()).orElseThrow();
        assertEquals(TrainingType.FREE_TEXT, training.getTrainingType());
        assertEquals(TrainingStatus.PLANNED, training.getStatus());
        assertEquals(0, participationRepository.findAllByTraining_IdOrderByTrainingDefinition_IdAscIdAsc(
                created.id()).size());
        List<TrainingSection> sections = sectionRepository
                .findAllByTraining_IdOrderBySequenceNumberAsc(created.id());
        assertEquals(List.of("Warm-up", "Main part"), sections.stream().map(TrainingSection::getTitle).toList());
        Long firstSectionId = sections.get(0).getId();
        Long secondSectionId = sections.get(1).getId();

        freeTextTrainingService.update(created.id(), new EditFreeTextTrainingRequest(
                "General session updated",
                LocalDate.parse("2026-03-03"),
                TrainingAssignmentType.GROUP,
                1L,
                List.of(),
                List.of(
                        new EditTrainingSectionRequest(secondSectionId, "Main part", "Updated drills", 1),
                        new EditTrainingSectionRequest(firstSectionId, "Warm-up", "Easy run", 2))));

        List<TrainingSection> reordered = sectionRepository
                .findAllByTraining_IdOrderBySequenceNumberAsc(created.id());
        assertEquals(List.of(secondSectionId, firstSectionId), reordered.stream().map(TrainingSection::getId).toList());
        assertEquals("Updated drills", reordered.get(0).getContent());
        assertEquals("General session updated", trainingRepository.findById(created.id()).orElseThrow().getName());

        freeTextTrainingService.markCompleted(created.id());
        assertEquals(TrainingStatus.COMPLETED, trainingRepository.findById(created.id()).orElseThrow().getStatus());
        assertThrows(InvalidTrainingRequestException.class, () ->
                trainingCompletionService.completeTraining(created.id(), new CompleteTrainingRequest(
                        List.of(), List.of(), List.of())));

        trainingDeletionService.deleteTraining(created.id());
        assertEquals(0, sectionRepository.findAllByTraining_IdOrderBySequenceNumberAsc(created.id()).size());
    }

    @Test
    void undatedFreeTextRequiresDateBeforeCompletionAndCannotClearCompletedDate() {
        var created = freeTextTrainingService.create(new CreateFreeTextTrainingRequest(
                "Undated", null, TrainingAssignmentType.GROUP, 1L, List.of(),
                List.of(new CreateTrainingSectionRequest("Warm-up", "Run", 1))));
        assertEquals(TrainingStatus.PLANNED, trainingRepository.findById(created.id()).orElseThrow().getStatus());
        assertThrows(InvalidTrainingRequestException.class, () -> freeTextTrainingService.markCompleted(created.id()));
        var section = sectionRepository.findAllByTraining_IdOrderBySequenceNumberAsc(created.id()).getFirst();
        var dated = new EditFreeTextTrainingRequest("Undated", LocalDate.parse("2026-03-03"),
                TrainingAssignmentType.GROUP, 1L, List.of(),
                List.of(new EditTrainingSectionRequest(section.getId(), "Warm-up", "Run", 1)));
        freeTextTrainingService.update(created.id(), dated);
        assertEquals(TrainingStatus.PLANNED, trainingRepository.findById(created.id()).orElseThrow().getStatus());
        freeTextTrainingService.markCompleted(created.id());
        var undated = new EditFreeTextTrainingRequest("Undated", null,
                TrainingAssignmentType.GROUP, 1L, List.of(), dated.sections());
        assertThrows(InvalidTrainingRequestException.class, () -> freeTextTrainingService.update(created.id(), undated));
        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update(
                "UPDATE training_sessions SET training_date = NULL WHERE id = ?", created.id()));
    }

    @Test
    void undatedTempoCannotBeCompletedOrHideExistingExecutionHistory() {
        var rows = insertTrainingRows();
        insertExecution(rows.participationId(), rows.segmentId(), rows.definitionId(), "NOT_COMPLETED");
        var definitions = List.of(editDefinition(rows.definitionId(), "Plan", 1,
                List.of(editSegment(rows.segmentId(), 1, 400, null)), List.of()));
        var noDate = new EditTrainingRequest("Training", null, definitions);
        assertThrows(InvalidTrainingRequestException.class,
                () -> trainingUpdateService.updateTraining(rows.trainingId(), noDate));
        jdbcTemplate.update("UPDATE training_segment_executions SET status = 'PENDING' WHERE participation_id = ?",
                rows.participationId());
        trainingUpdateService.updateTraining(rows.trainingId(), noDate);
        assertEquals(TrainingStatus.PLANNED, trainingRepository.findById(rows.trainingId()).orElseThrow().getStatus());
        assertTrue(athleteTrainingHistoryService.getHistory(1, null, null).isEmpty());
        assertThrows(InvalidTrainingRequestException.class, () -> trainingCompletionService.completeTraining(
                rows.trainingId(), new CompleteTrainingRequest(List.of(), List.of(), List.of())));
        trainingUpdateService.updateTraining(rows.trainingId(),
                new EditTrainingRequest("Training", LocalDate.parse("2026-03-04"), definitions));
        assertEquals(TrainingStatus.PLANNED, trainingRepository.findById(rows.trainingId()).orElseThrow().getStatus());
        trainingCompletionService.completeTraining(rows.trainingId(),
                new CompleteTrainingRequest(List.of(), List.of(), List.of()));
        assertEquals(TrainingStatus.COMPLETED, trainingRepository.findById(rows.trainingId()).orElseThrow().getStatus());
    }

    @Test
    void individualFreeTextAssignmentsExposeOnlySelectedAthletesAndRemainVisibleByIdOnlyToThem() {
        long oneAthleteTraining = createFreeTextTraining(
                "One athlete", TrainingAssignmentType.ATHLETES, null, List.of(1L));
        long multipleAthletesTraining = createFreeTextTraining(
                "Two athletes", TrainingAssignmentType.ATHLETES, null, List.of(1L, 2L));
        long tempoTraining = insertTraining("Tempo session");
        long athleteOneUser = insertAthleteAccount(1, "athlete-one");
        long athleteTwoUser = insertAthleteAccount(2, "athlete-two");
        long athleteThreeUser = insertAthleteAccount(3, "athlete-three");

        authenticate(athleteOneUser, UserRole.ATHLETE, "athlete-one");
        assertEquals(List.of(multipleAthletesTraining, oneAthleteTraining),
                trainingQueryService.listTrainings(true, null).stream().map(item -> item.id()).toList());
        assertEquals(List.of(), trainingQueryService.listTrainings(true, TrainingType.TEMPO));
        assertEquals(TrainingAssignmentType.ATHLETES,
                trainingDetailsService.getTraining(oneAthleteTraining).assignmentType());
        assertThrows(AccessDeniedException.class, () -> trainingDetailsService.getTraining(tempoTraining));

        authenticate(athleteTwoUser, UserRole.ATHLETE, "athlete-two");
        assertEquals(List.of(multipleAthletesTraining),
                trainingQueryService.listTrainings(true, null).stream().map(item -> item.id()).toList());
        trainingDetailsService.getTraining(multipleAthletesTraining);
        assertThrows(TrainingNotFoundException.class, () -> trainingDetailsService.getTraining(oneAthleteTraining));

        authenticate(athleteThreeUser, UserRole.ATHLETE, "athlete-three");
        assertEquals(List.of(), trainingQueryService.listTrainings(true, null));
        assertThrows(TrainingNotFoundException.class,
                () -> trainingDetailsService.getTraining(multipleAthletesTraining));
    }

    @Test
    void groupFreeTextVisibilityFollowsCurrentAthleteMembership() {
        long groupTraining = createFreeTextTraining(
                "Group session", TrainingAssignmentType.GROUP, 1L, List.of());
        long otherGroup = insertGroup("Other group");
        long athleteOneUser = insertAthleteAccount(1, "group-athlete-one");
        long athleteTwoUser = insertAthleteAccount(2, "group-athlete-two");
        long athleteThreeUser = insertAthleteAccount(3, "group-athlete-three");

        authenticate(athleteOneUser, UserRole.ATHLETE, "group-athlete-one");
        assertEquals(List.of(groupTraining),
                trainingQueryService.listTrainings(true, null).stream().map(item -> item.id()).toList());
        authenticate(athleteTwoUser, UserRole.ATHLETE, "group-athlete-two");
        trainingDetailsService.getTraining(groupTraining);

        jdbcTemplate.update("UPDATE athletes SET training_group_id = ? WHERE id = ?", otherGroup, 2);
        jdbcTemplate.update("UPDATE athletes SET training_group_id = ? WHERE id = ?", otherGroup, 3);

        authenticate(athleteTwoUser, UserRole.ATHLETE, "group-athlete-two");
        assertEquals(List.of(), trainingQueryService.listTrainings(true, null));
        assertThrows(TrainingNotFoundException.class, () -> trainingDetailsService.getTraining(groupTraining));
        authenticate(athleteThreeUser, UserRole.ATHLETE, "group-athlete-three");
        assertEquals(List.of(), trainingQueryService.listTrainings(true, null));
    }

    @Test
    void undatedFreeTextUsesExistingAthleteVisibilityAndNullFirstOrdering() {
        long dated = createFreeTextTraining("Dated", TrainingAssignmentType.GROUP, 1L, List.of());
        long undated = freeTextTrainingService.create(new CreateFreeTextTrainingRequest(
                "Undated", null, TrainingAssignmentType.GROUP, 1L, List.of(),
                List.of(new CreateTrainingSectionRequest("Section", "Content", 1)))).id();
        long athlete = insertAthleteAccount(1, "undated-athlete");
        authenticate(athlete, UserRole.ATHLETE, "undated-athlete");
        assertEquals(List.of(undated, dated),
                trainingQueryService.listTrainings(true, null).stream().map(item -> item.id()).toList());
        assertEquals(TrainingStatus.PLANNED, trainingDetailsService.getTraining(undated).status());
    }

    @Test
    void coachCanManageAthleteAssignedTrainingOnlyWhileAssignedToEverySelectedAthletesCurrentGroup() {
        long secondGroup = insertGroup("Second group");
        long thirdGroup = insertGroup("Third group");
        jdbcTemplate.update("UPDATE athletes SET training_group_id = ? WHERE id = ?", secondGroup, 2);
        long individualTraining = createFreeTextTraining(
                "Individual session", TrainingAssignmentType.ATHLETES, null, List.of(1L, 2L));
        long tempoTraining = insertTraining("Group tempo");
        long coachId = insertUser("multi-group-coach", "COACH");
        jdbcTemplate.update("INSERT INTO user_training_group (user_id, training_group_id) VALUES (?, 1)", coachId);
        jdbcTemplate.update(
                "INSERT INTO user_training_group (user_id, training_group_id) VALUES (?, ?)", coachId, secondGroup);

        authenticate(coachId, UserRole.COACH, "multi-group-coach");
        assertEquals(List.of(tempoTraining, individualTraining),
                trainingQueryService.listTrainings(true, null).stream().map(item -> item.id()).toList());
        trainingDetailsService.getTraining(individualTraining);
        freeTextTrainingService.update(individualTraining, new EditFreeTextTrainingRequest(
                "Updated individual session",
                LocalDate.parse("2026-03-05"),
                TrainingAssignmentType.ATHLETES,
                null,
                List.of(1L, 2L),
                List.of(new EditTrainingSectionRequest(null, "Section", "Updated content", 1))));

        jdbcTemplate.update("UPDATE athletes SET training_group_id = ? WHERE id = ?", thirdGroup, 2);
        assertEquals(List.of(tempoTraining),
                trainingQueryService.listTrainings(true, null).stream().map(item -> item.id()).toList());
        assertThrows(TrainingNotFoundException.class, () -> trainingDetailsService.getTraining(individualTraining));
        assertThrows(TrainingNotFoundException.class, () -> freeTextTrainingService.update(
                individualTraining,
                new EditFreeTextTrainingRequest(
                        "Not allowed",
                        LocalDate.parse("2026-03-05"),
                        TrainingAssignmentType.ATHLETES,
                        null,
                        List.of(1L, 2L),
                        List.of(new EditTrainingSectionRequest(null, "Section", "Content", 1)))));
    }

    @Test
    void athleteAssignmentRejectsInactiveAthletesAndLegacyFreeTextRowsRemainGroupAssigned() {
        long legacyTraining = insertTraining("Legacy free text");
        jdbcTemplate.update("UPDATE training_sessions SET training_type = 'FREE_TEXT' WHERE id = ?", legacyTraining);
        assertEquals(1L, jdbcTemplate.queryForObject(
                "SELECT training_group_id FROM training_sessions WHERE id = ?", Long.class, legacyTraining));
        assertEquals("GROUP", jdbcTemplate.queryForObject(
                "SELECT assignment_type FROM training_sessions WHERE id = ?", String.class, legacyTraining));

        jdbcTemplate.update("UPDATE athletes SET active = FALSE WHERE id = 3");
        assertThrows(AthleteNotFoundException.class, () -> createFreeTextTraining(
                "Inactive assignment", TrainingAssignmentType.ATHLETES, null, List.of(3L)));
    }

    @Test
    void v19MigrationPreservesExistingTrainingData() throws Exception {
        String schema = "migration_" + UUID.randomUUID().toString().replace("-", "");
        try {
            Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                    .schemas(schema).defaultSchema(schema).target(MigrationVersion.fromVersion("18"))
                    .load().migrate();
            try (var connection = DriverManager.getConnection(
                    POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                    var statement = connection.createStatement()) {
                statement.execute("INSERT INTO " + schema + ".training_groups (id, name) VALUES (1, 'Legacy')");
                statement.execute("INSERT INTO " + schema + ".training_sessions "
                        + "(id, name, training_group_id, training_type) VALUES "
                        + "(1, 'General', 1, 'FREE_TEXT'), (2, 'Tempo', 1, 'TEMPO')");
                statement.execute("INSERT INTO " + schema + ".training_sections "
                        + "(training_session_id, title, content, sequence_number) VALUES (1, 'Warmup', 'Run', 1)");
                statement.execute("INSERT INTO " + schema + ".users (id, username, password_hash, role) "
                        + "VALUES (1, 'legacy', 'hash', 'ADMIN')");
                statement.execute("INSERT INTO " + schema + ".training_imports "
                        + "(id, training_group_id, initiated_by_user_id, training_type) VALUES (1, 1, 1, 'FREE_TEXT')");
                statement.execute("INSERT INTO " + schema + ".training_import_attempts "
                        + "(id, import_id, attempt_number, model_name, status, started_at) "
                        + "VALUES (1, 1, 1, 'model', 'PROCESSING', CURRENT_TIMESTAMP)");
                statement.execute("INSERT INTO " + schema + ".training_import_drafts "
                        + "(import_id, generated_by_attempt_id, name) VALUES (1, 1, 'Legacy draft')");
            }
            Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                    .schemas(schema).defaultSchema(schema).load().migrate();
            try (var connection = DriverManager.getConnection(
                    POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                    var statement = connection.createStatement();
                    var result = statement.executeQuery("SELECT t.name, t.equipment, s.content FROM " + schema
                            + ".training_sessions t LEFT JOIN " + schema
                            + ".training_sections s ON s.training_session_id = t.id ORDER BY t.id")) {
                assertTrue(result.next());
                assertEquals("General", result.getString("name"));
                org.junit.jupiter.api.Assertions.assertNull(result.getString("equipment"));
                assertEquals("Run", result.getString("content"));
                assertTrue(result.next());
                assertEquals("Tempo", result.getString("name"));
                org.junit.jupiter.api.Assertions.assertNull(result.getString("equipment"));
            }
            try (var connection = DriverManager.getConnection(
                    POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                    var statement = connection.createStatement();
                    var result = statement.executeQuery(
                            "SELECT name, equipment FROM " + schema + ".training_import_drafts")) {
                assertTrue(result.next());
                assertEquals("Legacy draft", result.getString("name"));
                org.junit.jupiter.api.Assertions.assertNull(result.getString("equipment"));
            }
        } finally {
            try (var connection = DriverManager.getConnection(
                    POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                    var statement = connection.createStatement()) {
                statement.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
            }
        }
    }

    @Test
    void v16MigrationPreservesExistingFreeTextGroupAssignment() throws Exception {
        String schema = "migration_" + UUID.randomUUID().toString().replace("-", "");
        try {
            Flyway.configure()
                    .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                    .schemas(schema)
                    .defaultSchema(schema)
                    .target(MigrationVersion.fromVersion("15"))
                    .load()
                    .migrate();

            long groupId;
            long trainingId;
            try (var connection = DriverManager.getConnection(
                    POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                    var statement = connection.createStatement();
                    var groupResult = statement.executeQuery(
                            "INSERT INTO " + schema + ".training_groups (name) VALUES ('Legacy group') RETURNING id")) {
                groupResult.next();
                groupId = groupResult.getLong(1);
            }
            try (var connection = DriverManager.getConnection(
                    POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                    var statement = connection.createStatement();
                    var trainingResult = statement.executeQuery(
                            "INSERT INTO " + schema + ".training_sessions "
                                    + "(name, training_date, training_group_id, status, training_type) "
                                    + "VALUES ('Legacy free text', DATE '2026-03-05', " + groupId
                                    + ", 'PLANNED', 'FREE_TEXT') RETURNING id")) {
                trainingResult.next();
                trainingId = trainingResult.getLong(1);
            }

            Flyway.configure()
                    .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                    .schemas(schema)
                    .defaultSchema(schema)
                    .load()
                    .migrate();

            try (var connection = DriverManager.getConnection(
                    POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                    var statement = connection.createStatement();
                    var result = statement.executeQuery(
                            "SELECT training_group_id, assignment_type, training_date, status FROM " + schema
                                    + ".training_sessions WHERE id = " + trainingId)) {
                assertTrue(result.next());
                assertEquals(groupId, result.getLong("training_group_id"));
                assertEquals("GROUP", result.getString("assignment_type"));
                assertEquals(LocalDate.parse("2026-03-05"), result.getObject("training_date", LocalDate.class));
                assertEquals("PLANNED", result.getString("status"));
            }
        } finally {
            try (var connection = DriverManager.getConnection(
                    POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                    var statement = connection.createStatement()) {
                statement.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
            }
        }
    }

    @Test
    void coachOnlyGetsPlannedTrainingsInAssignedGroupsAndCanIncludeCompletedTrainings() {
        long assignedGroup = insertGroup("Assigned group");
        long unassignedGroup = insertGroup("Unassigned group");
        long firstTraining = insertTraining("Assigned recent", assignedGroup, LocalDate.parse("2026-05-01"));
        long secondTraining = insertTraining("Assigned older", 1, LocalDate.parse("2026-02-01"));
        long hiddenTraining = insertTraining("Unassigned", unassignedGroup, LocalDate.parse("2026-06-01"));
        long completedTraining = insertTraining("Completed", assignedGroup, LocalDate.parse("2026-07-01"));
        jdbcTemplate.update("UPDATE training_sessions SET status = 'COMPLETED' WHERE id = ?", completedTraining);

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
        var trainings = trainingQueryService.listTrainings(false, null);

        assertEquals(java.util.List.of(firstTraining, secondTraining),
                trainings.stream().map(item -> item.id()).toList());
        assertEquals(2L, trainings.get(0).participantCount());
        assertEquals(1L, trainings.get(0).definitionCount());
        assertEquals(assignedGroup, trainings.get(0).trainingGroup().id());
        var allTrainings = trainingQueryService.listTrainings(true, null);
        assertEquals(java.util.List.of(completedTraining, firstTraining, secondTraining),
                allTrainings.stream().map(item -> item.id()).toList());
    }

    @Test
    void rejectsAuthenticatedUsersWithoutCoachOrAdminRole() {
        authenticate(1, UserRole.ATHLETE, "athlete-one");

        assertThrows(AccessDeniedException.class, () -> trainingQueryService.listTrainings(false, null));
        assertThrows(AccessDeniedException.class, () ->
                trainingCreationService.createTraining(basicTrainingRequest(1, List.of())));
        assertThrows(AccessDeniedException.class, () ->
                trainingUpdateService.updateTraining(1, emptyEditRequest("Training", null)));
    }

    @Test
    void adminCreatesTrainingPlanWithoutParticipantsOrExecutions() {
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
                "SELECT COUNT(*) FROM training_sessions WHERE id = ? AND training_group_id = ? AND status = 'PLANNED'",
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
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM training_athlete_participations WHERE training_session_id = ?",
                Integer.class, response.id()));
        assertEquals(0, jdbcTemplate.queryForObject("""
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

        assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM training_sessions", Integer.class));
    }

    @Test
    void creationDoesNotCreateParticipantsFromAthleteIds() {
        long otherGroup = insertGroup("Other group");
        jdbcTemplate.update("UPDATE athletes SET training_group_id = ? WHERE id = 2", otherGroup);
        long trainingId = trainingCreationService.createTraining(basicTrainingRequest(1, List.of(999L))).id();
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM training_athlete_participations WHERE training_session_id = ?",
                Integer.class, trainingId));
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
                BEFORE INSERT ON training_segments
                FOR EACH ROW EXECUTE FUNCTION reject_training_execution_insert()
                """);
        try {
            assertThrows(RuntimeException.class, () -> trainingCreationService.createTraining(
                    basicTrainingRequest(1, List.of())));
            assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM training_sessions", Integer.class));
            assertEquals(0, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM training_athlete_participations", Integer.class));
        } finally {
            jdbcTemplate.execute("DROP TRIGGER IF EXISTS reject_training_execution_insert ON training_segments");
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
                    AND measured_result = 12.500 AND NOT uses_spikes
                """, Integer.class, recordedExecutionId, firstParticipationId, recordedSegmentId));
    }

    @Test
    void coachCanEditAssignedPlannedOrCompletedTrainingButNotUnassignedTraining() {
        long assignedTraining = insertTraining("Assigned", 1, LocalDate.now());
        long unassignedGroupId = insertGroup("Other");
        long unassignedTraining = insertTraining("Unassigned", unassignedGroupId, LocalDate.now());
        long completedTraining = insertTraining("Completed", 1, LocalDate.now());
        jdbcTemplate.update("UPDATE training_sessions SET status = 'COMPLETED' WHERE id = ?", completedTraining);
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
        assertEquals(completedTraining, trainingUpdateService.updateTraining(
                completedTraining, emptyEditRequest("Changed", null)).id());
        assertEquals("COMPLETED", jdbcTemplate.queryForObject(
                "SELECT status FROM training_sessions WHERE id = ?", String.class, completedTraining));
    }

    @Test
    void planEditsPreserveHistoricalParticipantsAfterAthleteGroupChange() {
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

        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM training_athlete_participations WHERE id = ?",
                Integer.class, participationId));
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
    void removingSegmentDeletesItsPendingExecutionsAndAddingSegmentCreatesPendingExecutions() {
        long trainingId = insertTraining("Training");
        long definitionId = insertDefinition(trainingId, "Plan", 1);
        long existingSegmentId = insertSegment(definitionId, 1);
        long removedSegmentId = insertSegment(definitionId, 2);
        long existingParticipationId = insertParticipation(trainingId, definitionId, 1);
        insertExecution(existingParticipationId, existingSegmentId, definitionId, "PENDING");
        insertExecution(existingParticipationId, removedSegmentId, definitionId, "PENDING");

        EditTrainingRequest request = editRequest(
                "Training", "2026-11-10", null,
                editDefinition(definitionId, "Plan", 1,
                        List.of(
                                editSegment(existingSegmentId, 1, 400, null),
                                editSegment(null, 2, 800, 30)),
                        List.of()));
        trainingUpdateService.updateTraining(trainingId, request);

        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM training_segments WHERE id = ?", Integer.class, removedSegmentId));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM training_athlete_participations WHERE id = ?",
                Integer.class, existingParticipationId));
        assertEquals(2, jdbcTemplate.queryForObject("""
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
    void planEditPreservesExecutionAndRollsBackFailedPendingInitialization() {
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
        trainingUpdateService.updateTraining(trainingId, invalidExecution);
        assertEquals(1, jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM training_segment_executions
                WHERE id = ? AND status = 'PENDING' AND measured_result IS NULL
                """, Integer.class, executionId));

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
            assertEquals("Changed", jdbcTemplate.queryForObject(
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
        assertThrows(InvalidTrainingRequestException.class, () ->
                trainingCompletionService.completeTraining(trainingId,
                        new CompleteTrainingRequest(List.of(), List.of(), List.of(participationId))));
    }

    @Test
    void completionAddsParticipantsWithPendingExecutionsAndUpdatesOnlyRequestedExecutions() {
        long trainingId = insertTraining("Training");
        long definitionId = insertDefinition(trainingId, "Plan", 1);
        long firstSegmentId = insertSegment(definitionId, 1);
        long secondSegmentId = insertSegment(definitionId, 2);
        long existingParticipationId = insertParticipation(trainingId, definitionId, 1);
        long firstExecutionId = jdbcTemplate.queryForObject("""
                INSERT INTO training_segment_executions
                    (participation_id, training_segment_id, training_definition_id, status)
                VALUES (?, ?, ?, 'PENDING')
                RETURNING id
                """, Long.class, existingParticipationId, firstSegmentId, definitionId);
        insertExecution(existingParticipationId, secondSegmentId, definitionId, "PENDING");
        CompleteTrainingRequest request = new CompleteTrainingRequest(
                List.of(new AddTrainingParticipationRequest(definitionId, 2L,
                        List.of(new NewTrainingExecutionRequest(
                                firstSegmentId, TrainingExecutionStatus.RECORDED,
                                new BigDecimal("14.250"), true)))),
                List.of(new UpdateTrainingParticipationRequest(existingParticipationId,
                        List.of(new EditTrainingExecutionRequest(
                                firstExecutionId, TrainingExecutionStatus.RECORDED,
                                new BigDecimal("12.500"), false)))),
                List.of());

        assertEquals(trainingId, trainingCompletionService.completeTraining(trainingId, request).id());
        assertEquals(2, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM training_athlete_participations WHERE training_session_id = ?",
                Integer.class, trainingId));
        assertEquals(4, jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM training_segment_executions e
                JOIN training_athlete_participations p ON p.id = e.participation_id
                WHERE p.training_session_id = ?
                """, Integer.class, trainingId));
        assertEquals(1, jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM training_segment_executions
                WHERE id = ? AND status = 'RECORDED' AND measured_result = 12.500 AND NOT uses_spikes
                """, Integer.class, firstExecutionId));
        assertEquals(1, jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM training_segment_executions e
                JOIN training_athlete_participations p ON p.id = e.participation_id
                WHERE p.training_session_id = ? AND p.athlete_id = 2 AND e.training_segment_id = ?
                    AND e.status = 'RECORDED' AND e.measured_result = 14.250 AND e.uses_spikes
                """, Integer.class, trainingId, firstSegmentId));
        assertEquals(1, jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM training_segment_executions e
                JOIN training_athlete_participations p ON p.id = e.participation_id
                WHERE p.training_session_id = ? AND p.athlete_id = 2 AND e.training_segment_id = ?
                    AND e.status = 'PENDING' AND e.measured_result IS NULL AND NOT e.uses_spikes
                """, Integer.class, trainingId, secondSegmentId));
        assertEquals(TrainingStatus.COMPLETED,
                trainingRepository.findById(trainingId).orElseThrow().getStatus());
    }

    @Test
    void completionAllowsHistoricalParticipantsButRejectsNewAthletesOutsideCurrentGroup() {
        long trainingId = insertTraining("Training");
        long definitionId = insertDefinition(trainingId, "Plan", 1);
        long segmentId = insertSegment(definitionId, 1);
        long participationId = insertParticipation(trainingId, definitionId, 1);
        long executionId = jdbcTemplate.queryForObject("""
                INSERT INTO training_segment_executions
                    (participation_id, training_segment_id, training_definition_id, status)
                VALUES (?, ?, ?, 'PENDING')
                RETURNING id
                """, Long.class, participationId, segmentId, definitionId);
        long otherGroup = insertGroup("Moved athlete");
        jdbcTemplate.update("UPDATE athletes SET training_group_id = ? WHERE id = 1", otherGroup);

        assertThrows(InvalidTrainingRequestException.class, () ->
                trainingCompletionService.completeTraining(trainingId, new CompleteTrainingRequest(
                        List.of(new AddTrainingParticipationRequest(definitionId, 1L, List.of())),
                        List.of(new UpdateTrainingParticipationRequest(participationId, List.of(
                                new EditTrainingExecutionRequest(
                                        executionId, TrainingExecutionStatus.RECORDED,
                                        new BigDecimal("11.000"), false)))),
                        List.of())));
        assertEquals(TrainingStatus.PLANNED,
                trainingRepository.findById(trainingId).orElseThrow().getStatus());
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM training_segment_executions WHERE id = ? AND status = 'PENDING'",
                Integer.class, executionId));

        trainingCompletionService.completeTraining(trainingId, new CompleteTrainingRequest(
                List.of(),
                List.of(new UpdateTrainingParticipationRequest(participationId, List.of(
                        new EditTrainingExecutionRequest(
                                executionId, TrainingExecutionStatus.RECORDED,
                                new BigDecimal("11.000"), false)))),
                List.of()));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM training_segment_executions WHERE id = ? AND status = 'RECORDED'",
                Integer.class, executionId));
    }

    @Test
    void completionRemovesEligibleParticipationsAndRejectsRemovalOfRecordedOnes() {
        long trainingId = insertTraining("Training");
        long definitionId = insertDefinition(trainingId, "Plan", 1);
        long segmentId = insertSegment(definitionId, 1);
        long pendingParticipationId = insertParticipation(trainingId, definitionId, 1);
        insertExecution(pendingParticipationId, segmentId, definitionId, "PENDING");
        long recordedParticipationId = insertParticipation(trainingId, definitionId, 2);
        jdbcTemplate.update("""
                INSERT INTO training_segment_executions
                    (participation_id, training_segment_id, training_definition_id, status, measured_result)
                VALUES (?, ?, ?, 'RECORDED', 10)
                """, recordedParticipationId, segmentId, definitionId);

        assertThrows(InvalidTrainingRequestException.class, () ->
                trainingCompletionService.completeTraining(trainingId,
                        new CompleteTrainingRequest(List.of(), List.of(), List.of(recordedParticipationId))));
        trainingCompletionService.completeTraining(trainingId,
                new CompleteTrainingRequest(List.of(), List.of(), List.of(pendingParticipationId)));

        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM training_athlete_participations WHERE id = ?",
                Integer.class, pendingParticipationId));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM training_segment_executions WHERE participation_id = ?",
                Integer.class, pendingParticipationId));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM training_athlete_participations WHERE id = ?",
                Integer.class, recordedParticipationId));
    }

    @Test
    void adminViewsPlannedAndCompletedTrainingWithOrderedCompleteHistory() {
        long plannedId = insertTraining("Planned", 1, LocalDate.parse("2026-03-01"));
        long completedId = insertTraining("Completed", 1, LocalDate.parse("2026-04-01"));
        jdbcTemplate.update("UPDATE training_sessions SET status = 'COMPLETED' WHERE id = ?", completedId);
        long secondDefinitionId = insertDefinition(plannedId, "Second", 2);
        long firstDefinitionId = insertDefinition(plannedId, "First", 1);
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
        long aliceParticipationId = insertParticipation(plannedId, firstDefinitionId, aliceId);
        long bobParticipationId = insertParticipation(plannedId, firstDefinitionId, bobId);
        insertExecution(aliceParticipationId, firstSegmentId, firstDefinitionId, "PENDING");
        long recordedExecutionId = jdbcTemplate.queryForObject("""
                INSERT INTO training_segment_executions
                    (participation_id, training_segment_id, training_definition_id,
                        status, measured_result, uses_spikes)
                VALUES (?, ?, ?, 'RECORDED', 12.340, TRUE) RETURNING id
                """, Long.class, aliceParticipationId, secondSegmentId, firstDefinitionId);
        insertExecution(bobParticipationId, firstSegmentId, firstDefinitionId, "COMPLETED_WITHOUT_RESULT");
        insertExecution(bobParticipationId, secondSegmentId, firstDefinitionId, "NOT_COMPLETED");
        long secondDefinitionParticipation = insertParticipation(plannedId, secondDefinitionId, 3);
        insertExecution(secondDefinitionParticipation, anotherDefinitionSegmentId, secondDefinitionId, "PENDING");
        jdbcTemplate.update("UPDATE athletes SET training_group_id = NULL WHERE id = ?", aliceId);

        TrainingDetailsResponse details = trainingDetailsService.getTraining(plannedId);
        assertEquals(plannedId, details.id());
        assertEquals(TrainingStatus.PLANNED, details.status());
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

        TrainingDetailsResponse completedDetails = trainingDetailsService.getTraining(completedId);
        assertEquals(TrainingStatus.COMPLETED, completedDetails.status());
        assertTrue(completedDetails.definitions().isEmpty());
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
    void rejectsMissingTrainingAndDeniesTempoDetailsToAthletes() {
        assertThrows(TrainingNotFoundException.class, () -> trainingDetailsService.getTraining(999));
        long tempoTraining = insertTraining("Tempo");
        authenticate(1, UserRole.ATHLETE, "athlete-view");
        assertThrows(AccessDeniedException.class, () -> trainingDetailsService.getTraining(tempoTraining));
    }

    @Test
    void adminDeletesCompletedTrainingAndAllRelatedDataIncludingRecordedResults() {
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
        jdbcTemplate.update("UPDATE training_sessions SET status = 'COMPLETED' WHERE id = ?", trainingId);

        trainingDeletionService.deleteTraining(trainingId);

        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM training_sessions WHERE id = ?", Integer.class, trainingId));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM training_definitions WHERE id = ?", Integer.class, definitionId));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM training_segments WHERE id = ?", Integer.class, segmentId));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM training_athlete_participations WHERE id = ?",
                Integer.class, participationId));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM training_segment_executions WHERE participation_id = ?",
                Integer.class, participationId));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM training_groups WHERE id = 1", Integer.class));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM athletes WHERE id = 1", Integer.class));
        assertEquals(0, trainingQueryService.listTrainings(true, null).stream()
                .filter(item -> item.id() == trainingId).count());
        assertThrows(TrainingNotFoundException.class, () ->
                trainingDetailsService.getTraining(trainingId));
    }

    @Test
    void coachCanDeleteAssignedTrainingButCannotDeleteUnassignedTraining() {
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

        trainingDeletionService.deleteTraining(assignedTraining);
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM training_sessions WHERE id = ?", Integer.class, assignedTraining));
        assertThrows(TrainingNotFoundException.class, () ->
                trainingDeletionService.deleteTraining(unassignedTraining));
    }

    @Test
    void rejectsDeletionByUnauthorizedUserAndForMissingTraining() {
        assertThrows(TrainingNotFoundException.class, () ->
                trainingDeletionService.deleteTraining(999));
        authenticate(1, UserRole.ATHLETE, "athlete-deactivate");
        assertThrows(AccessDeniedException.class, () ->
                trainingDeletionService.deleteTraining(1));
    }

    @Test
    void athleteHistoryPreservesDefinitionSegmentsStatusesAndInclusiveDates() {
        LocalDate date = LocalDate.parse("2026-11-11");
        long training = insertTraining("Intervals", 1, date);
        long definition = insertDefinition(training, "Main", 1);
        long first = insertSegment(definition, 1);
        long second = insertSegment(definition, 2);
        long third = insertSegment(definition, 3);
        long fourth = insertSegment(definition, 4);
        long participant = insertParticipation(training, definition, 1);
        jdbcTemplate.update("""
                INSERT INTO training_segment_executions
                    (participation_id, training_segment_id, training_definition_id, status, measured_result, uses_spikes)
                VALUES (?, ?, ?, 'RECORDED', 33.125, TRUE)
                """, participant, first, definition);
        insertExecution(participant, second, definition, "COMPLETED_WITHOUT_RESULT");
        insertExecution(participant, third, definition, "NOT_COMPLETED");
        insertExecution(participant, fourth, definition, "PENDING");
        long otherDefinition = insertDefinition(training, "Other", 2);
        long otherSegment = insertSegment(otherDefinition, 1);
        long otherParticipant = insertParticipation(training, otherDefinition, 2);
        insertExecution(otherParticipant, otherSegment, otherDefinition, "COMPLETED_WITHOUT_RESULT");
        long pendingTraining = insertTraining("Pending", 1, date.plusDays(1));
        long pendingDefinition = insertDefinition(pendingTraining, "Pending", 1);
        long pendingSegment = insertSegment(pendingDefinition, 1);
        long pendingParticipant = insertParticipation(pendingTraining, pendingDefinition, 1);
        insertExecution(pendingParticipant, pendingSegment, pendingDefinition, "PENDING");

        var history = athleteTrainingHistoryService.getHistory(1, date, date);
        assertEquals(1, history.size());
        assertEquals(training, history.getFirst().trainingId());
        assertTrue(history.getFirst().canViewDetails());
        assertEquals(4, history.getFirst().totalSegments());
        assertEquals(List.of(first, second, third, fourth), history.getFirst().segments().stream()
                .map(segment -> segment.segmentId()).toList());
        assertEquals(List.of(1, 2, 3, 4), history.getFirst().segments().stream()
                .map(segment -> segment.sequenceNumber()).toList());
        assertEquals(List.of(TrainingExecutionStatus.RECORDED, TrainingExecutionStatus.COMPLETED_WITHOUT_RESULT,
                        TrainingExecutionStatus.NOT_COMPLETED, TrainingExecutionStatus.PENDING),
                history.getFirst().segments().stream().map(segment -> segment.status()).toList());
        assertEquals(new BigDecimal("33.125"), history.getFirst().segments().getFirst().measuredResult());
        assertTrue(history.getFirst().segments().getFirst().usesSpikes());
        assertTrue(athleteTrainingHistoryService.getHistory(1, date.plusDays(1), null).isEmpty());
        assertTrue(athleteTrainingHistoryService.getHistory(3, null, null).isEmpty());
        assertThrows(InvalidTrainingRequestException.class,
                () -> athleteTrainingHistoryService.getHistory(1, date.plusDays(1), date));
    }

    @Test
    void athleteHistoryUsesCurrentAthleteGroupButHistoricalGroupForDetails() {
        long oldGroupTraining = insertTraining("Old", 1, LocalDate.parse("2026-01-01"));
        long definition = insertDefinition(oldGroupTraining, "Plan", 1);
        long segment = insertSegment(definition, 1);
        long participant = insertParticipation(oldGroupTraining, definition, 1);
        insertExecution(participant, segment, definition, "NOT_COMPLETED");
        long newGroup = insertGroup("New group");
        jdbcTemplate.update("UPDATE athletes SET training_group_id = ? WHERE id = 1", newGroup);
        long currentTraining = insertTraining("New", newGroup, LocalDate.parse("2026-02-01"));
        long newDefinition = insertDefinition(currentTraining, "Plan", 1);
        long newSegment = insertSegment(newDefinition, 1);
        long newParticipant = insertParticipation(currentTraining, newDefinition, 1);
        insertExecution(newParticipant, newSegment, newDefinition, "COMPLETED_WITHOUT_RESULT");
        jdbcTemplate.update("INSERT INTO users (username, password_hash, role) VALUES ('history-coach', 'hash', 'COACH')");
        jdbcTemplate.update("INSERT INTO user_training_group (user_id, training_group_id) VALUES (1, ?)", newGroup);
        authenticate(1, UserRole.COACH, "history-coach");

        var history = athleteTrainingHistoryService.getHistory(1, null, null);
        assertEquals(List.of(currentTraining, oldGroupTraining), history.stream().map(item -> item.trainingId()).toList());
        assertEquals(List.of(true, false), history.stream().map(item -> item.canViewDetails()).toList());
        assertThrows(TrainingNotFoundException.class, () -> trainingDetailsService.getTraining(oldGroupTraining));
        assertThrows(AthleteNotFoundException.class, () -> athleteTrainingHistoryService.getHistory(2, null, null));
        jdbcTemplate.update("DELETE FROM user_training_group WHERE user_id = 1");
        assertThrows(AthleteNotFoundException.class, () -> athleteTrainingHistoryService.getHistory(1, null, null));

        jdbcTemplate.update("INSERT INTO user_athlete_access (user_id, athlete_id) VALUES (1, 1)");
        authenticate(1, UserRole.ATHLETE, "athlete");
        assertEquals(List.of(false, false), athleteTrainingHistoryService.getHistory(1, null, null).stream()
                .map(item -> item.canViewDetails()).toList());
        assertThrows(AthleteNotFoundException.class, () -> athleteTrainingHistoryService.getHistory(2, null, null));
        assertThrows(AccessDeniedException.class, () -> trainingDetailsService.getTraining(currentTraining));
    }

    @Test
    void athleteHistorySortsSameDaySessionsAndRetainsPersistedSequenceNumbers() {
        LocalDate date = LocalDate.parse("2026-03-05");
        long older = insertTraining("Older", 1, date.minusDays(1));
        long first = insertTraining("First", 1, date);
        long second = insertTraining("Second", 1, date);
        for (long training : List.of(older, first, second)) {
            long definition = insertDefinition(training, "Plan", 1);
            long segment = insertSegment(definition, 3);
            long participant = insertParticipation(training, definition, 1);
            jdbcTemplate.update("""
                    INSERT INTO training_segment_executions
                        (participation_id, training_segment_id, training_definition_id, status, measured_result)
                    VALUES (?, ?, ?, 'RECORDED', 0)
                    """, participant, segment, definition);
            if (training == second) {
                long another = insertSegment(definition, 7);
                insertExecution(participant, another, definition, "PENDING");
            }
        }
        var history = athleteTrainingHistoryService.getHistory(1, null, date);
        assertEquals(List.of(second, first, older), history.stream().map(item -> item.trainingId()).toList());
        assertEquals(List.of(2, 1, 1), history.stream().map(item -> item.totalSegments()).toList());
        assertEquals(List.of(3, 7), history.getFirst().segments().stream()
                .map(item -> item.sequenceNumber()).toList());
        assertEquals(new BigDecimal("0.000"), history.getFirst().segments().getFirst().measuredResult());
        assertEquals(List.of(second, first), athleteTrainingHistoryService.getHistory(1, date, date).stream()
                .map(item -> item.trainingId()).toList());
    }

    @Test
    void athleteHistoryFailsWhenAnIncludedSegmentHasNoExecution() {
        var rows = insertTrainingRows();
        insertExecution(rows.participationId(), rows.segmentId(), rows.definitionId(), "NOT_COMPLETED");
        insertSegment(rows.definitionId(), 2);
        assertThrows(InvalidDataAccessApiUsageException.class,
                () -> athleteTrainingHistoryService.getHistory(1, null, null));
    }

    @Test
    void historyHttpEndpointEnforcesAuthenticationAndRoleScope() throws Exception {
        var rows = insertTrainingRows();
        insertExecution(rows.participationId(), rows.segmentId(), rows.definitionId(), "NOT_COMPLETED");
        String password = "history-test-password";
        for (String role : List.of("ADMIN", "COACH", "ATHLETE")) {
            jdbcTemplate.update("INSERT INTO users (username, password_hash, role) VALUES (?, ?, ?)",
                    role.toLowerCase(), passwordEncoder.encode(password), role);
        }
        jdbcTemplate.update("INSERT INTO user_training_group (user_id, training_group_id) VALUES (2, 1)");
        jdbcTemplate.update("INSERT INTO user_athlete_access (user_id, athlete_id) VALUES (3, 1)");
        SecurityContextHolder.clearContext();

        mockMvc.perform(get("/api/athletes/1/training-history")).andExpect(status().isUnauthorized());
        MockHttpSession admin = loginForHistory("admin", password);
        MockHttpSession coach = loginForHistory("coach", password);
        MockHttpSession athlete = loginForHistory("athlete", password);
        mockMvc.perform(get("/api/athletes/1/training-history").session(admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].canViewDetails").value(true));
        mockMvc.perform(get("/api/athletes/1/training-history").session(coach))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].canViewDetails").value(true));
        mockMvc.perform(get("/api/athletes/1/training-history").session(athlete))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].canViewDetails").value(false));
        mockMvc.perform(get("/api/athletes/1/training-history").session(athlete)
                        .queryParam("fromDate", "2026-03-02").queryParam("toDate", "2026-03-01"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/athletes/1/training-history").session(athlete)
                        .queryParam("fromDate", "not-a-date"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/athletes/0/training-history").session(athlete))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/athletes/2/training-history").session(athlete))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/trainings/" + rows.trainingId()).session(athlete))
                .andExpect(status().isForbidden());
        jdbcTemplate.update("DELETE FROM user_training_group WHERE user_id = 2");
        mockMvc.perform(get("/api/athletes/1/training-history").session(coach))
                .andExpect(status().isNotFound());
    }

    private MockHttpSession loginForHistory(String username, String password) throws Exception {
        var csrfResult = mockMvc.perform(get("/api/csrf")).andExpect(status().isOk()).andReturn();
        Cookie csrf = csrfResult.getResponse().getCookie("XSRF-TOKEN");
        var result = mockMvc.perform(post("/api/auth/login").cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue()).contentType("application/json")
                        .content("""
                                {"username":"%s","password":"%s"}
                                """.formatted(username, password)))
                .andExpect(status().isOk()).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
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

    private long createFreeTextTraining(
            String name,
            TrainingAssignmentType assignmentType,
            Long trainingGroupId,
            List<Long> athleteIds) {
        return freeTextTrainingService.create(new CreateFreeTextTrainingRequest(
                name,
                LocalDate.parse("2026-03-04"),
                assignmentType,
                trainingGroupId,
                athleteIds,
                List.of(new CreateTrainingSectionRequest("Section", "Content", 1)))).id();
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

    private long insertAthleteAccount(long athleteId, String username) {
        long userId = insertUser(username, "ATHLETE");
        jdbcTemplate.update(
                "INSERT INTO user_athlete_access (user_id, athlete_id) VALUES (?, ?)", userId, athleteId);
        return userId;
    }

    private long insertUser(String username, String role) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO users (username, password_hash, role)
                VALUES (?, 'test-hash', ?)
                RETURNING id
                """, Long.class, username, role);
    }

    private CreateTrainingRequest basicTrainingRequest(long groupId, List<Long> athleteIds) {
        return new CreateTrainingRequest("Training", LocalDate.parse("2026-11-10"), groupId,
                List.of(definitionRequest("Plan", 1,
                        List.of(new CreateTrainingSegmentRequest(1, 400, null)), List.of())));
    }

    private EditTrainingRequest emptyEditRequest(String name, Long groupId) {
        return editRequest(name, "2026-11-10", groupId,
                editDefinition(null, "Plan", 1,
                        List.of(editSegment(null, 1, 400, null)), List.of()));
    }

    private EditTrainingRequest editRequest(
            String name, String date, Long groupId, EditTrainingDefinitionRequest... definitions) {
        return new EditTrainingRequest(name, LocalDate.parse(date), List.of(definitions));
    }

    private EditTrainingDefinitionRequest editDefinition(
            Long id,
            String name,
            int sequenceNumber,
            List<EditTrainingSegmentRequest> segments,
            List<EditTrainingParticipationRequest> participations) {
        return new EditTrainingDefinitionRequest(id, name, sequenceNumber, segments);
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
        return new CreateTrainingDefinitionRequest(name, sequenceNumber, segments);
    }

    private record TrainingRows(
            long trainingId,
            long definitionId,
            long segmentId,
            long participationId,
            long athleteId) {
    }
}

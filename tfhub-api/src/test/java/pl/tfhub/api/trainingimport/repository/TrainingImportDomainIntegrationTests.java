package pl.tfhub.api.trainingimport.repository;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.Map;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import javax.imageio.ImageIO;
import pl.tfhub.api.common.authorization.AccessScopeService;
import pl.tfhub.api.athlete.entity.Athlete;
import pl.tfhub.api.athlete.repository.AthleteRepository;
import pl.tfhub.api.athlete.service.AthleteService;
import pl.tfhub.api.athlete.dto.request.UpdateAthleteRequest;
import pl.tfhub.api.athlete.enums.Gender;
import pl.tfhub.api.athlete.exception.AthleteNotFoundException;
import pl.tfhub.api.training.entity.Training;
import pl.tfhub.api.training.enums.TrainingExecutionStatus;
import pl.tfhub.api.training.repository.TrainingRepository;
import pl.tfhub.api.training.service.TrainingDeletionService;
import pl.tfhub.api.training.service.TrainingDetailsService;
import pl.tfhub.api.traininggroup.repository.TrainingGroupRepository;
import pl.tfhub.api.traininggroup.service.TrainingGroupService;
import pl.tfhub.api.trainingimport.entity.TrainingImport;
import pl.tfhub.api.trainingimport.entity.TrainingImportAttempt;
import pl.tfhub.api.trainingimport.entity.TrainingImportDraft;
import pl.tfhub.api.trainingimport.entity.TrainingImportDraftAthleteRow;
import pl.tfhub.api.trainingimport.entity.TrainingImportDraftDefinition;
import pl.tfhub.api.trainingimport.entity.TrainingImportDraftExecution;
import pl.tfhub.api.trainingimport.entity.TrainingImportDraftSegment;
import pl.tfhub.api.trainingimport.entity.TrainingImportSourceImage;
import pl.tfhub.api.trainingimport.dto.request.TrainingImportDraftUpdateRequest;
import pl.tfhub.api.trainingimport.dto.response.TrainingImportDraftResponse;
import pl.tfhub.api.trainingimport.enums.TrainingImportAthleteMatchStatus;
import pl.tfhub.api.trainingimport.enums.TrainingImportAttemptStatus;
import pl.tfhub.api.trainingimport.enums.TrainingImportStatus;
import pl.tfhub.api.trainingimport.dto.response.TrainingImportUploadResponse;
import pl.tfhub.api.trainingimport.exception.InvalidTrainingImportDraftException;
import pl.tfhub.api.trainingimport.exception.TrainingImportNotFoundException;
import pl.tfhub.api.trainingimport.exception.TrainingImportNotReviewableException;
import pl.tfhub.api.trainingimport.exception.TrainingImportImageStorageException;
import pl.tfhub.api.trainingimport.config.TrainingImportProperties;
import pl.tfhub.api.trainingimport.repository.TrainingImportAttemptRepository;
import pl.tfhub.api.trainingimport.repository.TrainingImportDraftAthleteRowRepository;
import pl.tfhub.api.trainingimport.repository.TrainingImportDraftDefinitionRepository;
import pl.tfhub.api.trainingimport.repository.TrainingImportDraftExecutionRepository;
import pl.tfhub.api.trainingimport.repository.TrainingImportDraftRepository;
import pl.tfhub.api.trainingimport.repository.TrainingImportDraftSegmentRepository;
import pl.tfhub.api.trainingimport.repository.TrainingImportRepository;
import pl.tfhub.api.trainingimport.repository.TrainingImportSourceImageRepository;
import pl.tfhub.api.trainingimport.service.TrainingImportDraftService;
import pl.tfhub.api.trainingimport.service.TrainingImportFinalizationService;
import pl.tfhub.api.trainingimport.service.GeminiTrainingRecognitionClient;
import pl.tfhub.api.trainingimport.service.TrainingImportImageProcessor;
import pl.tfhub.api.trainingimport.service.TrainingImportImageStorage;
import pl.tfhub.api.trainingimport.service.TrainingImportProcessingService;
import pl.tfhub.api.trainingimport.service.TrainingImportProcessingStateService;
import pl.tfhub.api.trainingimport.service.TrainingImportRecognitionMapper;
import pl.tfhub.api.trainingimport.service.TrainingImportSourceImageService;
import pl.tfhub.api.trainingimport.service.TrainingImportUploadService;
import pl.tfhub.api.trainingimport.service.TrainingImportListService;
import pl.tfhub.api.user.entity.User;
import pl.tfhub.api.user.enums.UserRole;
import pl.tfhub.api.user.repository.UserRepository;
import pl.tfhub.api.user.service.AccountManagementService;
import pl.tfhub.api.user.service.AuthenticatedUser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@Testcontainers
class TrainingImportDomainIntegrationTests {

	private static final String TEST_HASH = "a".repeat(64);
	private static final Path IMAGE_DIRECTORY =
			Path.of("target", "training-import-domain-test-images").toAbsolutePath().normalize();

	@Container
	private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

	@DynamicPropertySource
	static void configureDatabase(org.springframework.test.context.DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		registry.add("spring.datasource.username", POSTGRES::getUsername);
		registry.add("spring.datasource.password", POSTGRES::getPassword);
		registry.add("tfhub.training-import.storage-path", () -> IMAGE_DIRECTORY.toString());
	}

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private TrainingImportRepository trainingImportRepository;

	@Autowired
	private TrainingImportAttemptRepository attemptRepository;

	@Autowired
	private TrainingImportDraftRepository draftRepository;

	@Autowired
	private TrainingImportDraftDefinitionRepository definitionRepository;

	@Autowired
	private TrainingImportDraftSegmentRepository segmentRepository;

	@Autowired
	private TrainingImportDraftAthleteRowRepository athleteRowRepository;

	@Autowired
	private TrainingImportDraftExecutionRepository executionRepository;

	@Autowired
	private TrainingImportSourceImageRepository sourceImageRepository;

	@Autowired
	private AthleteRepository athleteRepository;

	@Autowired
	private AthleteService athleteService;

	@Autowired
	private TrainingGroupRepository trainingGroupRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private TrainingRepository trainingRepository;

	@Autowired
	private TrainingDetailsService trainingDetailsService;

	@Autowired
	private TrainingDeletionService trainingDeletionService;

	@Autowired
	private TrainingImportUploadService uploadService;

	@Autowired
	private TrainingImportListService importListService;

	@Autowired
	private TrainingImportProcessingService processingService;

	@Autowired
	private TrainingImportProcessingStateService processingStateService;

	@Autowired
	private TrainingImportProperties trainingImportProperties;

	@MockitoBean
	private GeminiTrainingRecognitionClient geminiClient;

	@Autowired
	private TrainingImportDraftService draftService;

	@Autowired
	private TrainingImportRecognitionMapper recognitionMapper;

	@Autowired
	private TrainingImportFinalizationService finalizationService;

	@Autowired
	private TrainingImportSourceImageService sourceImageService;

	@Autowired
	private TrainingImportImageProcessor imageProcessor;

	@Autowired
	private TrainingImportImageStorage imageStorage;

	@Autowired
	private TrainingGroupService trainingGroupService;

	@Autowired
	private AccountManagementService accountManagementService;

	@Autowired
	private AccessScopeService accessScopeService;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@BeforeEach
	void resetDatabase() throws IOException {
		clearImageDirectory(IMAGE_DIRECTORY);
		jdbcTemplate.execute("""
				TRUNCATE TABLE training_import_source_images, training_imports,
					training_segment_executions, training_athlete_participations,
					training_segments, training_definitions, training_sessions,
					athletes, training_groups, users RESTART IDENTITY CASCADE
				""");
		jdbcTemplate.update("INSERT INTO training_groups (name) VALUES ('Group A'), ('Group B')");
		jdbcTemplate.update("""
				INSERT INTO users (username, password_hash, role)
				VALUES ('test-admin', 'not-used', 'ADMIN'),
					('test-coach', 'not-used', 'COACH'),
					('test-athlete', 'not-used', 'ATHLETE')
				""");
		jdbcTemplate.update("INSERT INTO user_training_group (user_id, training_group_id) VALUES (2, 1)");
		jdbcTemplate.update("""
				INSERT INTO athletes (first_name, last_name, birth_year, gender, training_group_id)
				VALUES ('Ada', 'Lovelace', 1990, 'FEMALE', 1),
					('Bea', 'Example', 1991, 'FEMALE', 2)
				""");
		setAuthenticatedUser(1, UserRole.ADMIN);
	}

	@AfterEach
	void clearSecurityContext() throws IOException {
		trainingImportProperties.setProcessingEnabled(false);
		SecurityContextHolder.clearContext();
		clearImageDirectory(IMAGE_DIRECTORY);
		Files.deleteIfExists(IMAGE_DIRECTORY);
	}

	@Test
	void persistsImportAndItsSourceImageWithGroupUserAndPendingStatus() {
		TrainingImport trainingImport = saveImportWithImage("first.jpg", TEST_HASH);

		TrainingImport persisted = trainingImportRepository.findById(trainingImport.getId()).orElseThrow();
		TrainingImportSourceImage sourceImage =
				sourceImageRepository.findById(trainingImport.getId()).orElseThrow();

		assertEquals(TrainingImportStatus.PENDING, persisted.getStatus());
		assertFalse(persisted.isPossibleDuplicate());
		assertEquals(1L, persisted.getTrainingGroup().getId());
		assertEquals(1L, persisted.getInitiatedBy().getId());
		assertNotNull(persisted.getCreatedAt());
		assertEquals(trainingImport.getId(), sourceImage.getImportId());
		assertEquals("image/jpeg", sourceImage.getMediaType());
		assertEquals("first.jpg", sourceImage.getOriginalFilename());
		assertEquals(120L, sourceImage.getByteSize());
		assertEquals(800, sourceImage.getWidth());
		assertEquals(600, sourceImage.getHeight());
		assertEquals(TEST_HASH, sourceImage.getSha256());
		assertEquals(trainingImport.getId(), sourceImage.getTrainingImport().getId());
	}

	@Test
	void allowsDuplicateHashesForSeparateImports() {
		TrainingImport first = saveImportWithImage("first.jpg", TEST_HASH);
		TrainingImport second = saveImportWithImage("second.jpg", TEST_HASH);

		assertFalse(first.getId().equals(second.getId()));
		assertFalse(first.isPossibleDuplicate());
		assertTrue(second.isPossibleDuplicate());
		assertTrue(sourceImageRepository.existsBySha256(TEST_HASH));
		assertEquals(2L, sourceImageRepository.countBySha256(TEST_HASH));
	}

	@Test
	void listsOnlyActiveImportsWithinCurrentGroupScopeWithSafeSummaryData() {
		TrainingImport pending = saveImportWithImage(1, "pending.jpg", TEST_HASH);
		TrainingImport failed = saveImportWithImage(1, "failed.jpg", TEST_HASH);
		TrainingImport review = saveReviewDraft(2, "review.jpg");
		TrainingImport rejected = saveImportWithImage(1, "rejected.jpg", TEST_HASH);
		TrainingImport processing = saveImportWithImage(2, "processing.jpg", TEST_HASH);
		TrainingImport accepted = saveImportWithImage(1, "accepted.jpg", TEST_HASH);
		jdbcTemplate.update("UPDATE training_imports SET status = 'PROCESSING' WHERE id = ?", processing.getId());
		jdbcTemplate.update("UPDATE training_imports SET status = 'FAILED', next_retry_at = CURRENT_TIMESTAMP WHERE id = ?",
				failed.getId());
		jdbcTemplate.update("UPDATE training_imports SET status = 'REJECTED' WHERE id = ?", rejected.getId());
		jdbcTemplate.update("""
				INSERT INTO training_sessions (id, name, training_date, training_group_id)
				VALUES (1, 'Accepted training', CURRENT_DATE, 1)
				""");
		jdbcTemplate.update("""
				UPDATE training_imports SET status = 'ACCEPTED', accepted_training_session_id = 1
				WHERE id = ?
				""", accepted.getId());
		jdbcTemplate.update("""
				INSERT INTO training_import_attempts
					(import_id, attempt_number, model_name, status, started_at, finished_at, raw_model_response, failure_message)
				VALUES (?, 1, 'model', 'FAILED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'private response', 'Safe failure')
				""", failed.getId());
		jdbcTemplate.update("""
				INSERT INTO training_import_attempts
					(import_id, attempt_number, model_name, status, started_at, finished_at, failure_message)
				VALUES (?, 2, 'model', 'FAILED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'Latest safe failure')
				""", failed.getId());

		var adminList = importListService.listActive();
		assertEquals(4, adminList.size());
		assertEquals(processing.getId(), adminList.getFirst().id());
		var failedSummary = adminList.stream().filter(item -> item.id() == failed.getId()).findFirst().orElseThrow();
		assertEquals("Group A", failedSummary.trainingGroupName());
		assertEquals("Latest safe failure", failedSummary.failureMessage());
		assertNotNull(failedSummary.nextRetryAt());
		var reviewSummary = adminList.stream().filter(item -> item.id() == review.getId()).findFirst().orElseThrow();
		assertNotNull(reviewSummary.name());
		assertNotNull(reviewSummary.trainingDate());

		setAuthenticatedUser(2, UserRole.COACH);
		var coachList = importListService.listActive();
		assertEquals(2, coachList.size());
		assertTrue(coachList.stream().anyMatch(item -> item.id() == pending.getId()));
		assertFalse(coachList.stream().anyMatch(item -> item.id() == review.getId()));
		jdbcTemplate.update("DELETE FROM user_training_group WHERE user_id = 2 AND training_group_id = 1");
		assertTrue(importListService.listActive().isEmpty());

		setAuthenticatedUser(3, UserRole.ATHLETE);
		assertThrows(AccessDeniedException.class, () -> importListService.listActive());
		SecurityContextHolder.clearContext();
		assertThrows(AccessDeniedException.class, () -> importListService.listActive());
	}

	@Test
	void uploadStoresOptimizedJpegAndDoesNotCreateTraining() throws IOException {
		long originalTrainingCount = count("training_sessions");

		TrainingImportUploadResponse response = uploadService.upload(1, createUpload("photo.png"));

		TrainingImportSourceImage sourceImage = sourceImageRepository.findById(response.id()).orElseThrow();
		Path storedImage = IMAGE_DIRECTORY.resolve(sourceImage.getStorageKey());
		assertEquals(1L, response.trainingGroupId());
		assertEquals(TrainingImportStatus.PENDING, response.status());
		assertFalse(response.possibleDuplicate());
		assertEquals("image/jpeg", sourceImage.getMediaType());
		assertEquals("photo.png", sourceImage.getOriginalFilename());
		assertTrue(Files.exists(storedImage));
		byte[] storedBytes = Files.readAllBytes(storedImage);
		assertEquals((byte) 0xff, storedBytes[0]);
		assertEquals((byte) 0xd8, storedBytes[1]);
		assertNotNull(ImageIO.read(new java.io.ByteArrayInputStream(storedBytes)));
		try (var files = Files.list(IMAGE_DIRECTORY)) {
			assertEquals(1L, files.count());
		}
		assertEquals(originalTrainingCount, count("training_sessions"));
	}

	@Test
	void duplicateUploadCreatesASeparateImportAndReportsDuplicate() throws IOException {
		TrainingImportUploadResponse first = uploadService.upload(1, createUpload("first.png"));
		TrainingImportUploadResponse second = uploadService.upload(1, createUpload("second.png"));

		assertFalse(first.possibleDuplicate());
		assertTrue(second.possibleDuplicate());
		assertFalse(first.id() == second.id());
		assertEquals(2L, count("training_imports"));
	}

	@Test
	void adminMayUploadForAnyExistingGroupAndCoachOnlyForAssignedGroup() throws IOException {
		TrainingImportUploadResponse adminUpload = uploadService.upload(2, createUpload("admin.png"));
		assertEquals(2L, adminUpload.trainingGroupId());

		setAuthenticatedUser(2, UserRole.COACH);
		TrainingImportUploadResponse coachUpload = uploadService.upload(1, createUpload("coach.png"));
		assertEquals(1L, coachUpload.trainingGroupId());

		assertThrows(AccessDeniedException.class, () -> uploadService.upload(2, createUpload("denied.png")));
		assertEquals(2L, count("training_imports"));
	}

	@Test
	void unauthenticatedAndAthleteUsersCannotUpload() throws IOException {
		SecurityContextHolder.clearContext();
		assertThrows(AccessDeniedException.class, () -> uploadService.upload(1, createUpload("anonymous.png")));

		setAuthenticatedUser(3, UserRole.ATHLETE);
		assertThrows(AccessDeniedException.class, () -> uploadService.upload(1, createUpload("athlete.png")));
		assertEquals(0L, count("training_imports"));
	}

	@Test
	void storageFailureCreatesNoImport() throws IOException {
		Path fileUsedAsStorageParent = IMAGE_DIRECTORY.resolve("not-a-directory");
		Files.writeString(fileUsedAsStorageParent, "test");
		TrainingImportImageStorage failingStorage = new TrainingImportImageStorage(
				propertiesWithStoragePath(fileUsedAsStorageParent.resolve("images")));
		TrainingImportUploadService service = newUploadService(sourceImageRepository, failingStorage);

		assertThrows(
				TrainingImportImageStorageException.class,
				() -> runInTransaction(service, createUpload("storage-failure.png")));
		assertEquals(0L, count("training_imports"));
	}

	@Test
	void databaseFailureRollsBackImportAndRemovesStoredImage() throws IOException {
		TrainingImportSourceImageRepository failingRepository = mock(TrainingImportSourceImageRepository.class);
		when(failingRepository.existsBySha256(any())).thenReturn(false);
		doThrow(new DataIntegrityViolationException("test failure"))
				.when(failingRepository).saveAndFlush(any(TrainingImportSourceImage.class));
		TrainingImportUploadService service = newUploadService(failingRepository, imageStorage);

		assertThrows(
				DataIntegrityViolationException.class,
				() -> runInTransaction(service, createUpload("database-failure.png")));
		assertEquals(0L, count("training_imports"));
		try (var files = Files.list(IMAGE_DIRECTORY)) {
			assertEquals(0L, files.count());
		}
	}

	@Test
	void newMigrationLeavesPersistedTrainingTablesUsable() {
		Training training = trainingRepository.saveAndFlush(
				new Training("Existing training", java.time.LocalDate.parse("2026-04-20"),
						trainingGroupRepository.findById(1L).orElseThrow()));

		assertNotNull(training.getId());
		assertEquals(1L, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM training_sessions", Long.class));
		assertEquals(0L, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM training_imports", Long.class));
	}

	@Test
	void retrievesCompleteDraftAndSearchesCandidatesOnlyInImportGroup() {
		TrainingImport groupOneImport = saveReviewDraft(1, "review-a.jpg");
		TrainingImport groupTwoImport = saveReviewDraft(2, "review-b.jpg");

		TrainingImportDraftResponse response = draftService.getDraft(groupOneImport.getId());

		assertEquals(TrainingImportStatus.REVIEW_REQUIRED, response.status());
		assertEquals(1L, response.trainingGroup().id());
		assertEquals("Group A", response.trainingGroup().name());
		assertEquals(1, response.definitions().size());
		assertEquals(2, response.definitions().getFirst().segments().size());
		assertEquals(1, response.definitions().getFirst().athleteRows().size());
		assertEquals("Ada", response.definitions().getFirst().athleteRows().getFirst().athlete().firstName());
		assertEquals(TrainingExecutionStatus.RECORDED,
				response.definitions().getFirst().athleteRows().getFirst().executions().getFirst().status());
		assertFalse(response.validation().readyForAcceptance());

		assertEquals(List.of("Ada"), draftService.findAthleteCandidates(groupOneImport.getId(), "ada")
				.stream().map(candidate -> candidate.firstName()).toList());
		assertEquals(List.of("Bea"), draftService.findAthleteCandidates(groupTwoImport.getId(), "bea")
				.stream().map(candidate -> candidate.firstName()).toList());

		setAuthenticatedUser(2, UserRole.COACH);
		assertEquals(1, draftService.findAthleteCandidates(groupOneImport.getId(), null).size());
		jdbcTemplate.update("DELETE FROM user_training_group WHERE user_id = 2 AND training_group_id = 1");
		assertThrows(TrainingImportNotFoundException.class,
				() -> draftService.getDraft(groupOneImport.getId()));
		assertThrows(TrainingImportNotFoundException.class,
				() -> draftService.findAthleteCandidates(groupOneImport.getId(), "ada"));
	}

	@Test
	void athleteAliasesFollowExistingCoachAccessScope() {
		jdbcTemplate.update("INSERT INTO athlete_aliases(athlete_id, alias) VALUES (1, 'Ada'), (2, 'Bea')");
		setAuthenticatedUser(2, UserRole.COACH);
		assertEquals(List.of("Ada"), athleteService.getAthlete(1L).aliases());
		assertThrows(AthleteNotFoundException.class, () -> athleteService.getAthlete(2L));
		assertThrows(AthleteNotFoundException.class, () -> athleteService.updateAthlete(2L,
				new UpdateAthleteRequest("Bea", "Example", 1991, Gender.FEMALE, 2L, List.of("new"))));
		assertEquals("Bea", jdbcTemplate.queryForObject(
				"SELECT alias FROM athlete_aliases WHERE athlete_id = 2", String.class));
	}

	@Test
	void suggestionsExcludeAthletesWhoAreInactiveOrOutsideTheImportGroup() {
		TrainingImport trainingImport = saveReviewDraft(1, "stale-suggestions.jpg");
		jdbcTemplate.update("""
				UPDATE training_import_draft_athlete_rows
				SET athlete_id = NULL, match_status = 'UNRESOLVED',
				    recognition_metadata = '{"uncertain":true,"suggestedAthleteIds":[1,2]}'::jsonb
				WHERE draft_id = (SELECT id FROM training_import_drafts WHERE import_id = ?)
				""", trainingImport.getId());
		var row = draftService.getDraft(trainingImport.getId()).definitions().getFirst().athleteRows().getFirst();
		assertNull(row.athlete());
		assertEquals(List.of(1L), row.recognitionMetadata().get("suggestedAthleteIds"));
		jdbcTemplate.update("UPDATE athletes SET active = false WHERE id = 1");
		row = draftService.getDraft(trainingImport.getId()).definitions().getFirst().athleteRows().getFirst();
		assertEquals(List.of(), row.recognitionMetadata().get("suggestedAthleteIds"));
		assertEquals(1, draftService.findAthleteCandidates(trainingImport.getId(), null).size());
	}

	@Test
	void unresolvedRowCanBeManuallyAssignedWithoutChangingOtherRecognizedData() {
		TrainingImport trainingImport = saveReviewDraft(1, "manual-match.jpg");
		jdbcTemplate.update("""
				UPDATE training_import_draft_athlete_rows SET athlete_id = NULL, match_status = 'UNRESOLVED'
				WHERE draft_id = (SELECT id FROM training_import_drafts WHERE import_id = ?)
				""", trainingImport.getId());
		var original = draftService.getDraft(trainingImport.getId());
		assertNull(original.definitions().getFirst().athleteRows().getFirst().athlete());
		var updated = draftService.updateDraft(trainingImport.getId(), updateRequest(original, 1, false));
		var row = updated.definitions().getFirst().athleteRows().getFirst();
		assertEquals(TrainingImportAthleteMatchStatus.MATCHED, row.matchStatus());
		assertEquals(1L, row.athlete().id());
		assertEquals("Ada Lovelace", row.recognizedName());
	}

	@Test
	void searchesCandidatesWithNullBlankAndTextQueriesInTheImportGroup() {
		TrainingImport trainingImport = saveReviewDraft(1, "candidate-search.jpg");
		jdbcTemplate.update("""
				INSERT INTO athletes (first_name, last_name, birth_year, gender, training_group_id)
				VALUES ('Zoe', 'alpha', 1992, 'FEMALE', 1),
					('Ada', 'Outside', 1993, 'FEMALE', 2)
				""");

		for (String query : new String[] {null, "", "   "}) {
			assertEquals(List.of("Zoe", "Ada"), draftService.findAthleteCandidates(trainingImport.getId(), query)
					.stream().map(candidate -> candidate.firstName()).toList());
		}
		assertEquals(List.of("Ada"), draftService.findAthleteCandidates(trainingImport.getId(), "  ADA LOVELACE  ")
				.stream().map(candidate -> candidate.firstName()).toList());
		assertTrue(draftService.findAthleteCandidates(trainingImport.getId(), "outside").isEmpty());
	}

	@Test
	void updatesDraftAtomicallyAndPreservesRecognitionDataWithoutCreatingTraining() {
		TrainingImport trainingImport = saveReviewDraft(1, "review-update.jpg");
		TrainingImportDraftResponse original = draftService.getDraft(trainingImport.getId());
		TrainingImportDraftUpdateRequest request = updateRequest(original, 1, false);

		TrainingImportDraftResponse updated = draftService.updateDraft(trainingImport.getId(), request);

		assertEquals("Corrected intervals", updated.name());
		assertEquals(TrainingImportStatus.REVIEW_REQUIRED, updated.status());
		assertTrue(updated.validation().readyForAcceptance());
		assertEquals(0L, count("training_sessions"));
		assertEquals(600, updated.definitions().getFirst().segments().get(1).distanceM());
		assertEquals(new BigDecimal("12.340"),
				updated.definitions().getFirst().athleteRows().getFirst().executions().get(1).measuredResult());
		assertTrue(updated.definitions().getFirst().athleteRows().getFirst()
				.executions().get(1).usesSpikes());
		assertEquals("12.3?", updated.definitions().getFirst().athleteRows().getFirst()
				.executions().get(1).recognizedText());
		assertEquals("Intervals?", updated.recognitionMetadata().get("originalText"));
		assertEquals("400?", updated.definitions().getFirst().segments().get(1)
				.recognitionMetadata().get("originalText"));

		TrainingImportDraftResponse beforeFailure = draftService.getDraft(trainingImport.getId());
		assertThrows(InvalidTrainingImportDraftException.class,
				() -> draftService.updateDraft(
						trainingImport.getId(), updateRequest(beforeFailure, 2, false)));
		assertEquals(beforeFailure, draftService.getDraft(trainingImport.getId()));
		assertEquals(0L, count("training_sessions"));
	}

	@Test
	void savesAndAcceptsAnAthleteAddedToANewDefinition() {
		TrainingImport trainingImport = saveReviewDraft(1, "new-athlete.jpg");
		jdbcTemplate.update("""
				INSERT INTO athletes (first_name, last_name, birth_year, gender, training_group_id)
				VALUES ('Zoe', 'Runner', 1992, 'FEMALE', 1)
				""");
		TrainingImportDraftUpdateRequest original = updateRequest(draftService.getDraft(trainingImport.getId()), 1, false);
		var added = new TrainingImportDraftUpdateRequest.Definition(
				null, "New subgroup", 2,
				List.of(
						new TrainingImportDraftUpdateRequest.Segment(null, 1, 100, null, false),
						new TrainingImportDraftUpdateRequest.Segment(null, 2, 200, null, false)),
				List.of(new TrainingImportDraftUpdateRequest.AthleteRow(
						null, "Zoe Runner", 3L, TrainingImportAthleteMatchStatus.MATCHED,
						List.of(
								new TrainingImportDraftUpdateRequest.Execution(
										null, 1, TrainingExecutionStatus.RECORDED, new BigDecimal("12.340"), false, false),
								new TrainingImportDraftUpdateRequest.Execution(
										null, 2, TrainingExecutionStatus.PENDING, null, false, false)))));
		var request = new TrainingImportDraftUpdateRequest(
				original.name(), original.trainingDate(), original.recognitionReviewed(),
				List.of(original.definitions().getFirst(), added));

		var updated = draftService.updateDraft(trainingImport.getId(), request);
		var row = updated.definitions().get(1).athleteRows().getFirst();
		assertTrue(updated.validation().readyForAcceptance());
		assertEquals("Zoe Runner", row.recognizedName());
		assertEquals(3L, row.athlete().id());
		assertEquals(2, row.executions().size());
		assertEquals(new BigDecimal("12.340"), row.executions().getFirst().measuredResult());
		var accepted = finalizationService.accept(trainingImport.getId());
		var training = trainingDetailsService.getTraining(accepted.trainingId());
		assertEquals(pl.tfhub.api.training.enums.TrainingStatus.COMPLETED, training.status());
		var addedExecutions = training.definitions().get(1).participations().getFirst().executions();
		assertTrue(addedExecutions.stream().anyMatch(execution -> execution.status() == TrainingExecutionStatus.PENDING));
		assertTrue(addedExecutions.stream().anyMatch(execution ->
				execution.status() == TrainingExecutionStatus.RECORDED
						&& new BigDecimal("12.340").compareTo(execution.measuredResult()) == 0));
		assertEquals(2L, count("training_athlete_participations"));
		assertEquals(4L, count("training_segment_executions"));
	}

	@Test
	void explicitSkippedAthleteRowsAreResolvedAndUnreviewableImportsAreRejected() {
		TrainingImport trainingImport = saveReviewDraft(1, "review-skip.jpg");
		TrainingImportDraftResponse initial = draftService.getDraft(trainingImport.getId());
		TrainingImportDraftUpdateRequest matchedRequest = updateRequest(initial, 1, true);

		TrainingImportDraftUpdateRequest.Definition definition = matchedRequest.definitions().getFirst();
		TrainingImportDraftUpdateRequest.AthleteRow row = definition.athleteRows().getFirst();
		TrainingImportDraftUpdateRequest skippedRequest = new TrainingImportDraftUpdateRequest(
				matchedRequest.name(),
				matchedRequest.trainingDate(),
				matchedRequest.recognitionReviewed(),
				List.of(new TrainingImportDraftUpdateRequest.Definition(
						definition.id(),
						definition.name(),
						definition.sequenceNumber(),
						definition.segments(),
						List.of(new TrainingImportDraftUpdateRequest.AthleteRow(
								row.id(),
								row.recognizedName(),
								null,
								TrainingImportAthleteMatchStatus.SKIPPED,
								row.executions())))));
		TrainingImportDraftResponse skipped = draftService.updateDraft(trainingImport.getId(), skippedRequest);
		assertTrue(skipped.validation().readyForAcceptance());
		assertEquals(TrainingImportAthleteMatchStatus.SKIPPED,
				skipped.definitions().getFirst().athleteRows().getFirst().matchStatus());

		TrainingImport pending = saveImportWithImage("pending.jpg", "c".repeat(64));
		assertThrows(TrainingImportNotReviewableException.class, () -> draftService.getDraft(pending.getId()));
	}

	@Test
	void acceptsReviewedDraftAtomicallyAndCreatesCompletedTrainingWithExecutionData() {
		TrainingImport trainingImport = saveReviewDraft(1, "accept.jpg");
		TrainingImportDraftResponse initial = draftService.getDraft(trainingImport.getId());
		TrainingImportDraftResponse reviewed = draftService.updateDraft(
				trainingImport.getId(), updateRequest(initial, 1, false));

		var accepted = finalizationService.accept(trainingImport.getId());

		assertEquals(trainingImport.getId(), accepted.importId());
		assertEquals(TrainingImportStatus.ACCEPTED, accepted.status());
		assertNotNull(accepted.trainingId());
		assertTrue(reviewed.validation().readyForAcceptance());
		TrainingImport persistedImport = trainingImportRepository.findById(trainingImport.getId()).orElseThrow();
		assertEquals(TrainingImportStatus.ACCEPTED, persistedImport.getStatus());
		assertEquals(accepted.trainingId(), persistedImport.getAcceptedTrainingSessionId());
		assertEquals(1L, count("training_import_source_images"));

		var training = trainingDetailsService.getTraining(accepted.trainingId());
		assertEquals("Corrected intervals", training.name());
		assertEquals(java.time.LocalDate.parse("2026-11-11"), training.trainingDate());
		assertEquals(pl.tfhub.api.training.enums.TrainingStatus.COMPLETED, training.status());
		assertEquals(1, training.definitions().size());
		assertEquals(2, training.definitions().getFirst().segments().size());
		assertEquals(1, training.definitions().getFirst().participations().size());
		var participation = training.definitions().getFirst().participations().getFirst();
		assertEquals(1L, participation.athleteId());
		assertEquals(2, participation.executions().size());
		var recordedExecution = participation.executions().stream()
				.filter(execution -> execution.status() == TrainingExecutionStatus.RECORDED)
				.findFirst()
				.orElseThrow();
		assertEquals(new BigDecimal("12.340"), recordedExecution.measuredResult());
		assertTrue(recordedExecution.usesSpikes());
		assertEquals(1L, count("training_sessions"));
		assertThrows(TrainingImportNotReviewableException.class,
				() -> finalizationService.accept(trainingImport.getId()));
	}

	@Test
	void skippedRowsAreOmittedAndFailedAcceptanceLeavesNoPartialTraining() {
		TrainingImport skippedImport = saveReviewDraft(1, "accept-skip.jpg");
		TrainingImportDraftResponse skippedDraft = draftService.getDraft(skippedImport.getId());
		draftService.updateDraft(skippedImport.getId(), updateRequest(skippedDraft, 1, true));

		var accepted = finalizationService.accept(skippedImport.getId());

		assertEquals(pl.tfhub.api.training.enums.TrainingStatus.COMPLETED,
				trainingDetailsService.getTraining(accepted.trainingId()).status());
		assertEquals(0L, jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM training_athlete_participations WHERE training_session_id = ?",
				Long.class, accepted.trainingId()));
		assertEquals(0L, jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM training_segment_executions WHERE training_definition_id IN "
						+ "(SELECT id FROM training_definitions WHERE training_session_id = ?)",
				Long.class, accepted.trainingId()));

		TrainingImport invalidImport = saveReviewDraft(1, "accept-group-change.jpg");
		TrainingImportDraftResponse initial = draftService.getDraft(invalidImport.getId());
		draftService.updateDraft(invalidImport.getId(), updateRequest(initial, 1, false));
		jdbcTemplate.update("UPDATE athletes SET training_group_id = 2 WHERE id = 1");

		assertThrows(InvalidTrainingImportDraftException.class,
				() -> finalizationService.accept(invalidImport.getId()));
		assertEquals(TrainingImportStatus.REVIEW_REQUIRED,
				trainingImportRepository.findById(invalidImport.getId()).orElseThrow().getStatus());
		assertEquals(1L, count("training_sessions"));
	}

	@Test
	void rejectionPreservesDraftAndAttemptsAndCannotBeFinalizedAgain() {
		TrainingImport trainingImport = saveReviewDraft(1, "reject.jpg");

		var rejected = finalizationService.reject(trainingImport.getId());

		assertEquals(trainingImport.getId(), rejected.importId());
		assertEquals(TrainingImportStatus.REJECTED, rejected.status());
		assertEquals(TrainingImportStatus.REJECTED, draftService.getDraft(trainingImport.getId()).status());
		assertEquals(1L, count("training_imports"));
		assertEquals(1L, count("training_import_attempts"));
		assertEquals(1L, count("training_import_drafts"));
		assertEquals(1L, count("training_import_source_images"));
		assertEquals(0L, count("training_sessions"));
		assertThrows(TrainingImportNotReviewableException.class,
				() -> finalizationService.accept(trainingImport.getId()));
	}

	@Test
	void acceptanceRejectsUnreviewedDraftWithoutCreatingTraining() {
		TrainingImport trainingImport = saveReviewDraft(1, "accept-unreviewed.jpg");

		assertThrows(InvalidTrainingImportDraftException.class,
				() -> finalizationService.accept(trainingImport.getId()));

		assertEquals(TrainingImportStatus.REVIEW_REQUIRED,
				trainingImportRepository.findById(trainingImport.getId()).orElseThrow().getStatus());
		assertEquals(0L, count("training_sessions"));
	}

	@Test
	void persistenceFailureRollsBackTrainingAndLeavesImportReviewable() {
		TrainingImport trainingImport = saveReviewDraft(1, "accept-rollback.jpg");
		TrainingImportDraftResponse initial = draftService.getDraft(trainingImport.getId());
		draftService.updateDraft(trainingImport.getId(), updateRequest(initial, 1, false));
		jdbcTemplate.execute("""
				CREATE FUNCTION test_fail_training_execution_insert() RETURNS trigger AS $$
				BEGIN
				    RAISE EXCEPTION 'injected execution persistence failure';
				END;
				$$ LANGUAGE plpgsql
				""");
		jdbcTemplate.execute("""
				CREATE TRIGGER test_fail_training_execution_insert
				BEFORE INSERT ON training_segment_executions
				FOR EACH ROW EXECUTE FUNCTION test_fail_training_execution_insert()
				""");
		try {
			assertThrows(RuntimeException.class,
					() -> finalizationService.accept(trainingImport.getId()));
		} finally {
			jdbcTemplate.execute("DROP TRIGGER test_fail_training_execution_insert ON training_segment_executions");
			jdbcTemplate.execute("DROP FUNCTION test_fail_training_execution_insert()");
		}

		assertEquals(0L, count("training_sessions"));
		assertEquals(TrainingImportStatus.REVIEW_REQUIRED,
				trainingImportRepository.findById(trainingImport.getId()).orElseThrow().getStatus());
	}

	@Test
	void coachesCannotFinalizeImportsFromUnassignedGroups() {
		TrainingImport trainingImport = saveReviewDraft(2, "coach-inaccessible.jpg");
		setAuthenticatedUser(2, UserRole.COACH);

		assertThrows(TrainingImportNotFoundException.class,
				() -> finalizationService.reject(trainingImport.getId()));
		assertEquals(TrainingImportStatus.REVIEW_REQUIRED,
				trainingImportRepository.findById(trainingImport.getId()).orElseThrow().getStatus());
	}

	@Test
	void deletingImportedTrainingAlsoDeletesItsImportAndStoredImage() throws IOException {
		TrainingImport trainingImport = saveReviewDraftWithStoredImage(
				1, "delete-after-accept.jpg", new byte[] {1, 2, 3});
		String storageKey = sourceImageRepository.findById(trainingImport.getId())
				.orElseThrow().getStorageKey();
		TrainingImportDraftResponse initial = draftService.getDraft(trainingImport.getId());
		draftService.updateDraft(trainingImport.getId(), updateRequest(initial, 1, false));
		var accepted = finalizationService.accept(trainingImport.getId());
		assertTrue(Files.exists(IMAGE_DIRECTORY.resolve(storageKey)));

		trainingDeletionService.deleteTraining(accepted.trainingId());

		assertEquals(0L, count("training_sessions"));
		assertEquals(0L, count("training_imports"));
		assertEquals(0L, count("training_import_attempts"));
		assertEquals(0L, count("training_import_drafts"));
		assertFalse(Files.exists(IMAGE_DIRECTORY.resolve(storageKey)));
	}

	@Test
	void databaseRejectsMatchingTheSameAthleteTwiceWithinOneDraft() {
		TrainingImport trainingImport = saveReviewDraft(1, "review-duplicate.jpg");
		TrainingImportDraft draft = draftRepository.findByTrainingImport_Id(trainingImport.getId()).orElseThrow();
		TrainingImportDraftDefinition definition =
				definitionRepository.findByDraft_IdOrderBySequenceNumberAsc(draft.getId()).getFirst();
		assertThrows(DataIntegrityViolationException.class, () -> athleteRowRepository.saveAndFlush(
				new TrainingImportDraftAthleteRow(
						draft,
						definition,
						athleteRepository.findById(1L).orElseThrow(),
						"Duplicate Ada",
						TrainingImportAthleteMatchStatus.MATCHED,
						Map.of())));
	}

	@Test
	void sourceImageRequiresCurrentGroupAuthorizationAndReturnsStoredJpegBytes() {
		byte[] contents = {(byte) 0xff, (byte) 0xd8, 1, 2};
		TrainingImport trainingImport = saveImportWithStoredImage(1, "source.jpg", "d".repeat(64), contents);

		assertEquals(List.of((byte) 0xff, (byte) 0xd8, (byte) 1, (byte) 2),
				toByteList(sourceImageService.getSourceImage(trainingImport.getId())));

		setAuthenticatedUser(2, UserRole.COACH);
		assertEquals(contents.length, sourceImageService.getSourceImage(trainingImport.getId()).length);
		jdbcTemplate.update("DELETE FROM user_training_group WHERE user_id = 2 AND training_group_id = 1");
		assertThrows(TrainingImportNotFoundException.class,
				() -> sourceImageService.getSourceImage(trainingImport.getId()));
	}

	@Test
	void processingPersistsValidatedDraftAndRawResponseWithoutExposingItInStatus() {
		TrainingImport trainingImport = saveImportWithStoredImage(
				1, "processing.jpg", "e".repeat(64), new byte[] {(byte) 0xff, (byte) 0xd8, 1, 2});
		String rawResponse = "{\"provider\":\"private raw response\"}";
		when(geminiClient.recognize(any())).thenReturn(
				new GeminiTrainingRecognitionClient.GeminiRecognitionResponse(
						rawResponse, validRecognition("Recognized session")));
		Long attemptId = processingStateService.claimPending(trainingImport.getId());

		processingService.processClaimedAttempt(attemptId);

		TrainingImportAttempt attempt = attemptRepository.findById(attemptId).orElseThrow();
		TrainingImportDraft draft = draftRepository.findByTrainingImport_Id(trainingImport.getId()).orElseThrow();
		var status = processingService.getStatus(trainingImport.getId());
		assertEquals(TrainingImportAttemptStatus.SUCCEEDED, attempt.getStatus());
		assertEquals(rawResponse, attempt.getRawModelResponse());
		assertEquals(TrainingImportStatus.REVIEW_REQUIRED, status.status());
		assertEquals("Recognized session", draft.getName());
		assertEquals(1L, count("training_import_draft_definitions"));
		assertEquals(1L, count("training_import_draft_segments"));
		assertEquals(1L, count("training_import_draft_athlete_rows"));
		assertEquals(1L, count("training_import_draft_executions"));
		assertEquals(0L, count("training_sessions"));
		assertFalse(status.toString().contains(rawResponse));
		verify(geminiClient, times(1)).recognize(any());
	}

	@Test
	void processingMatchesExplicitAliasAndPreservesExecutionData() {
		jdbcTemplate.update("INSERT INTO athlete_aliases(athlete_id, alias) VALUES (1, 'Ada L.')");
		TrainingImport trainingImport = saveImportWithStoredImage(
				1, "alias-match.jpg", "c".repeat(64), new byte[] {(byte) 0xff, (byte) 0xd8, 1, 2});
		String response = validRecognition("Recognized session").replace(
				"\"recognizedName\":\"Ada Lovelace\",\"firstName\":\"Ada\",\"lastName\":\"Lovelace\"",
				"\"recognizedName\":\" ADA L \",\"firstName\":\"Ada\",\"lastName\":null");
		when(geminiClient.recognize(any())).thenReturn(
				new GeminiTrainingRecognitionClient.GeminiRecognitionResponse("raw", response));
		processingService.processClaimedAttempt(processingStateService.claimPending(trainingImport.getId()));

		var draft = draftService.getDraft(trainingImport.getId());
		var row = draft.definitions().getFirst().athleteRows().getFirst();
		assertEquals(TrainingImportAthleteMatchStatus.MATCHED, row.matchStatus());
		assertEquals(1L, row.athlete().id());
		assertEquals(" ADA L ", row.recognizedName());
		assertEquals(TrainingExecutionStatus.RECORDED, row.executions().getFirst().status());
		assertEquals(new BigDecimal("12.300"), row.executions().getFirst().measuredResult());
	}

	@Test
	void conflictingAliasAndFullNameRemainUnresolvedForManualReview() {
		jdbcTemplate.update("""
				INSERT INTO athletes(first_name, last_name, birth_year, gender, training_group_id)
				VALUES ('Other', 'Runner', 1992, 'FEMALE', 1)
				""");
		jdbcTemplate.update("INSERT INTO athlete_aliases(athlete_id, alias) VALUES (3, 'Ada Lovelace')");
		TrainingImport trainingImport = saveImportWithStoredImage(
				1, "ambiguous.jpg", "d".repeat(64), new byte[] {(byte) 0xff, (byte) 0xd8, 1, 2});
		when(geminiClient.recognize(any())).thenReturn(
				new GeminiTrainingRecognitionClient.GeminiRecognitionResponse("raw", validRecognition("Session")));
		processingService.processClaimedAttempt(processingStateService.claimPending(trainingImport.getId()));

		var row = draftService.getDraft(trainingImport.getId()).definitions().getFirst().athleteRows().getFirst();
		assertEquals(TrainingImportAthleteMatchStatus.UNRESOLVED, row.matchStatus());
		assertNull(row.athlete());
		assertEquals(Boolean.TRUE, row.recognitionMetadata().get("uncertain"));
		assertEquals(2, draftService.findAthleteCandidates(trainingImport.getId(), null).size());
	}

	@Test
	void removedAliasIsRecheckedBeforeTheDraftIsPersisted() {
		jdbcTemplate.update("INSERT INTO athlete_aliases(athlete_id, alias) VALUES (1, 'Ada L.')");
		TrainingImport trainingImport = saveImportWithImage(1, "stale-alias.jpg", "f".repeat(64));
		Long attemptId = processingStateService.claimPending(trainingImport.getId());
		String response = validRecognition("Session").replace(
				"\"recognizedName\":\"Ada Lovelace\",\"firstName\":\"Ada\",\"lastName\":\"Lovelace\"",
				"\"recognizedName\":\"Ada L.\",\"firstName\":\"Ada\",\"lastName\":null");
		var recognizedDraft = recognitionMapper.validateAndMap(response, 1L);
		jdbcTemplate.update("DELETE FROM athlete_aliases WHERE athlete_id = 1");
		processingStateService.complete(attemptId, "raw", recognizedDraft);
		var row = draftService.getDraft(trainingImport.getId()).definitions().getFirst().athleteRows().getFirst();
		assertEquals(TrainingImportAthleteMatchStatus.UNRESOLVED, row.matchStatus());
		assertNull(row.athlete());
		assertEquals(Boolean.TRUE, row.recognitionMetadata().get("uncertain"));
	}

	@Test
	void blankRecognizedNamesBecomeEditableDraftDefaultsAndPersistOnAcceptance() {
		TrainingImport trainingImport = saveImportWithStoredImage(
				1, "blank-names.jpg", "e".repeat(64), new byte[] {(byte) 0xff, (byte) 0xd8, 1, 2});
		String response = validRecognition("")
				.replace("\"value\":\"Main set\"", "\"value\":null");
		when(geminiClient.recognize(any())).thenReturn(
				new GeminiTrainingRecognitionClient.GeminiRecognitionResponse("raw", response));
		Long attemptId = processingStateService.claimPending(trainingImport.getId());

		processingService.processClaimedAttempt(attemptId);

		assertEquals(TrainingImportAttemptStatus.SUCCEEDED, attemptRepository.findById(attemptId).orElseThrow().getStatus());
		var draft = draftService.getDraft(trainingImport.getId());
		assertEquals(TrainingImportStatus.REVIEW_REQUIRED, draft.status());
		assertEquals("Trening 10.11.2026", draft.name());
		assertEquals("Cała grupa", draft.definitions().getFirst().name());
		assertEquals(400, draft.definitions().getFirst().segments().getFirst().distanceM());
		assertEquals(new BigDecimal("12.300"),
				draft.definitions().getFirst().athleteRows().getFirst().executions().getFirst().measuredResult());
		assertTrue(draft.validation().readyForAcceptance());
		var accepted = finalizationService.accept(trainingImport.getId());
		var training = trainingDetailsService.getTraining(accepted.trainingId());
		assertEquals("Trening 10.11.2026", training.name());
		assertEquals("Cała grupa", training.definitions().getFirst().name());
	}

	@Test
	void missingFinalRestDefaultsToZeroWithoutConfirmation() {
		TrainingImport trainingImport = saveImportWithStoredImage(
				1, "missing-rest.jpg", "c".repeat(64), new byte[] {(byte) 0xff, (byte) 0xd8, 1, 2});
		String response = validRecognition("Recognized session").replace(
				"\"restAfterSeconds\":{\"value\":\"60\",\"originalText\":\"60 s\",\"uncertain\":false,\"uncertaintyReason\":null}",
				"\"restAfterSeconds\":null");
		when(geminiClient.recognize(any())).thenReturn(
				new GeminiTrainingRecognitionClient.GeminiRecognitionResponse("raw", response));
		Long attemptId = processingStateService.claimPending(trainingImport.getId());
		processingService.processClaimedAttempt(attemptId);

		assertEquals(TrainingImportAttemptStatus.SUCCEEDED, attemptRepository.findById(attemptId).orElseThrow().getStatus());
		var draft = draftService.getDraft(trainingImport.getId());
		assertEquals(TrainingImportStatus.REVIEW_REQUIRED, draft.status());
		var definition = draft.definitions().getFirst();
		var segment = definition.segments().getFirst();
		var row = definition.athleteRows().getFirst();
		var execution = row.executions().getFirst();
		assertEquals(0, segment.restAfterSeconds());
		assertEquals(400, segment.distanceM());
		assertEquals(Boolean.FALSE, segment.recognitionMetadata().get("uncertain"));
		assertEquals(new BigDecimal("12.300"), execution.measuredResult());
		assertTrue(draft.validation().readyForAcceptance());
		var accepted = finalizationService.accept(trainingImport.getId());
		var training = trainingDetailsService.getTraining(accepted.trainingId());
		assertEquals(0, training.definitions().getFirst().segments().getFirst().restAfterSeconds());
	}

	@Test
	void unresolvedDistanceSurvivesProcessingAndCanBeCorrectedBeforeAcceptance() {
		TrainingImport trainingImport = saveImportWithStoredImage(
				1, "unresolved-distance.jpg", "c".repeat(64), new byte[] {(byte) 0xff, (byte) 0xd8, 1, 2});
		when(geminiClient.recognize(any())).thenReturn(
				new GeminiTrainingRecognitionClient.GeminiRecognitionResponse(
						"raw", validRecognition("Recognized session").replace("\"value\":\"400\"", "\"value\":null")));
		Long attemptId = processingStateService.claimPending(trainingImport.getId());
		processingService.processClaimedAttempt(attemptId);

		assertEquals(TrainingImportAttemptStatus.SUCCEEDED, attemptRepository.findById(attemptId).orElseThrow().getStatus());
		TrainingImportDraftResponse response = draftService.getDraft(trainingImport.getId());
		assertEquals(TrainingImportStatus.REVIEW_REQUIRED, response.status());
		var definition = response.definitions().getFirst();
		var segment = definition.segments().getFirst();
		var row = definition.athleteRows().getFirst();
		assertNull(segment.distanceM());
		assertEquals(1, segment.sequenceNumber());
		assertEquals(60, segment.restAfterSeconds());
		assertEquals(Boolean.TRUE, segment.recognitionMetadata().get("uncertain"));
		assertEquals(new BigDecimal("12.300"), row.executions().getFirst().measuredResult());
		assertTrue(response.validation().issues().stream().anyMatch(issue ->
				issue.code().equals("SEGMENT_DISTANCE_UNRESOLVED") && issue.path().endsWith("/segments/1/distanceM")));
		var error = assertThrows(InvalidTrainingImportDraftException.class,
				() -> finalizationService.accept(trainingImport.getId()));
		assertTrue(error.getMessage().contains("Definition 1, segment 1"));
		assertEquals(0L, count("training_sessions"));

		var execution = row.executions().getFirst();
		TrainingImportDraftUpdateRequest request = new TrainingImportDraftUpdateRequest(
				response.name(), response.trainingDate(), false,
				List.of(new TrainingImportDraftUpdateRequest.Definition(
						definition.id(), definition.name(), definition.sequenceNumber(),
						List.of(new TrainingImportDraftUpdateRequest.Segment(
								segment.id(), segment.sequenceNumber(), 400, segment.restAfterSeconds(), true)),
						List.of(new TrainingImportDraftUpdateRequest.AthleteRow(
								row.id(), row.recognizedName(), row.athlete().id(), row.matchStatus(),
								List.of(new TrainingImportDraftUpdateRequest.Execution(
										execution.id(), execution.segmentSequenceNumber(), execution.status(),
										execution.measuredResult(), execution.usesSpikes(), false)))))));
		var corrected = draftService.updateDraft(trainingImport.getId(), request);
		assertTrue(corrected.validation().readyForAcceptance());
		var accepted = finalizationService.accept(trainingImport.getId());
		var training = trainingDetailsService.getTraining(accepted.trainingId());
		assertEquals(400, training.definitions().getFirst().segments().getFirst().distanceM());
	}

	@Test
	void unparseableDistanceIsPersistedForReview() {
		TrainingImport trainingImport = saveImportWithStoredImage(
				1, "unparseable-distance.jpg", "c".repeat(64), new byte[] {(byte) 0xff, (byte) 0xd8, 1, 2});
		when(geminiClient.recognize(any())).thenReturn(
				new GeminiTrainingRecognitionClient.GeminiRecognitionResponse(
						"raw", validRecognition("Recognized session").replace("\"value\":\"400\"", "\"value\":\"???\"")));
		Long attemptId = processingStateService.claimPending(trainingImport.getId());
		processingService.processClaimedAttempt(attemptId);
		var result = draftService.getDraft(trainingImport.getId());
		var segment = result.definitions().getFirst().segments().getFirst();
		assertEquals(TrainingImportStatus.REVIEW_REQUIRED, result.status());
		assertNull(segment.distanceM());
		assertEquals(Boolean.TRUE, segment.recognitionMetadata().get("uncertain"));
		assertEquals("Recognized session", result.name());
		assertEquals("12.3", result.definitions().getFirst().athleteRows().getFirst()
				.executions().getFirst().recognizedText());
	}

	@Test
	void reviewCanSaveAnOtherwiseResolvedDraftWithAnUnresolvedDistance() {
		TrainingImport trainingImport = saveReviewDraft(1, "partially-reviewed.jpg");
		var original = draftService.getDraft(trainingImport.getId());
		var valid = updateRequest(original, 1, false);
		var definition = valid.definitions().getFirst();
		var first = definition.segments().getFirst();
		var unresolved = new TrainingImportDraftUpdateRequest(
				valid.name(), valid.trainingDate(), valid.recognitionReviewed(),
				List.of(new TrainingImportDraftUpdateRequest.Definition(
						definition.id(), definition.name(), definition.sequenceNumber(),
						List.of(new TrainingImportDraftUpdateRequest.Segment(
										first.id(), first.sequenceNumber(), null, first.restAfterSeconds(), false),
								definition.segments().get(1)),
						definition.athleteRows())));
		var saved = draftService.updateDraft(trainingImport.getId(), unresolved);
		var segment = saved.definitions().getFirst().segments().get(1);
		assertNull(segment.distanceM());
		assertFalse(saved.validation().readyForAcceptance());
		assertTrue(saved.validation().issues().stream().anyMatch(issue -> issue.code().equals("SEGMENT_DISTANCE_UNRESOLVED")));
		assertEquals(2, saved.definitions().getFirst().segments().size());
		assertEquals(2, saved.definitions().getFirst().athleteRows().getFirst().executions().size());
	}

	@Test
	void invalidRecognitionFailsAttemptAndRetainsRawResponseWithoutTrainingWrites() {
		TrainingImport trainingImport = saveImportWithStoredImage(
				1, "invalid-processing.jpg", "f".repeat(64), new byte[] {(byte) 0xff, (byte) 0xd8, 1, 2});
		String rawResponse = "{\"provider\":\"invalid response body\"}";
		when(geminiClient.recognize(any())).thenReturn(
				new GeminiTrainingRecognitionClient.GeminiRecognitionResponse(rawResponse, "{invalid"));
		Long attemptId = processingStateService.claimPending(trainingImport.getId());

		processingService.processClaimedAttempt(attemptId);

		TrainingImportAttempt attempt = attemptRepository.findById(attemptId).orElseThrow();
		var status = processingService.getStatus(trainingImport.getId());
		assertEquals(TrainingImportAttemptStatus.FAILED, attempt.getStatus());
		assertEquals(rawResponse, attempt.getRawModelResponse());
		assertEquals("OUTPUT_VALIDATION_FAILED", status.latestAttempt().failureCode());
		assertFalse(status.toString().contains(rawResponse));
		assertEquals(0L, count("training_import_drafts"));
		assertEquals(0L, count("training_sessions"));
	}

	@Test
	void retryReplacesDraftOnlyAfterSuccessAndFailedRetryPreservesExistingDraft() {
		TrainingImport trainingImport = saveReviewDraft(1, "retry.jpg");
		String storageKey = imageStorage.store(new byte[] {(byte) 0xff, (byte) 0xd8, 1, 2});
		jdbcTemplate.update(
				"UPDATE training_import_source_images SET storage_key = ? WHERE import_id = ?",
				storageKey, trainingImport.getId());
		TrainingImportDraft originalDraft =
				draftRepository.findByTrainingImport_Id(trainingImport.getId()).orElseThrow();
		String originalDraftName = originalDraft.getName();
		trainingImport.changeStatus(TrainingImportStatus.FAILED);
		trainingImportRepository.saveAndFlush(trainingImport);

		String failedRawResponse = "{\"provider\":\"failed retry\"}";
		when(geminiClient.recognize(any())).thenReturn(
				new GeminiTrainingRecognitionClient.GeminiRecognitionResponse(
						failedRawResponse, "{\"definitions\":[]}"));
		Long failedAttemptId = processingStateService.claimRetry(trainingImport.getId());
		processingService.processClaimedAttempt(failedAttemptId);
		TrainingImportDraft afterFailure =
				draftRepository.findByTrainingImport_Id(trainingImport.getId()).orElseThrow();
		assertEquals(originalDraft.getId(), afterFailure.getId());
		assertEquals(originalDraftName, afterFailure.getName());
		assertEquals(TrainingImportStatus.FAILED,
				trainingImportRepository.findById(trainingImport.getId()).orElseThrow().getStatus());

		trainingImport = trainingImportRepository.findById(trainingImport.getId()).orElseThrow();
		String successfulRawResponse = "{\"provider\":\"successful retry\"}";
		when(geminiClient.recognize(any())).thenReturn(
				new GeminiTrainingRecognitionClient.GeminiRecognitionResponse(
						successfulRawResponse, validRecognition("Replacement session")));
		Long successfulAttemptId = processingStateService.claimRetry(trainingImport.getId());
		processingService.processClaimedAttempt(successfulAttemptId);

		TrainingImportAttempt successfulAttempt =
				attemptRepository.findById(successfulAttemptId).orElseThrow();
		assertEquals(TrainingImportAttemptStatus.SUCCEEDED, successfulAttempt.getStatus(),
				successfulAttempt.getFailureCode() + ": " + successfulAttempt.getFailureMessage());
		TrainingImportDraft afterSuccess =
				draftRepository.findByTrainingImport_Id(trainingImport.getId()).orElseThrow();
		assertEquals(originalDraft.getId(), afterSuccess.getId());
		assertEquals("Replacement session", afterSuccess.getName());
		assertEquals(successfulAttemptId, afterSuccess.getGeneratedByAttempt().getId());
		assertEquals(TrainingImportStatus.REVIEW_REQUIRED,
				trainingImportRepository.findById(trainingImport.getId()).orElseThrow().getStatus());
		assertEquals(TrainingImportAttemptStatus.FAILED,
				attemptRepository.findById(failedAttemptId).orElseThrow().getStatus());
		assertEquals(failedRawResponse,
				attemptRepository.findById(failedAttemptId).orElseThrow().getRawModelResponse());
		assertEquals(successfulRawResponse,
				attemptRepository.findById(successfulAttemptId).orElseThrow().getRawModelResponse());
		assertEquals(0L, count("training_sessions"));
		verify(geminiClient, times(2)).recognize(any());
	}

	@Test
	void retryDoesNotReplaceManuallyReviewedAthleteAssignments() {
		TrainingImport trainingImport = saveReviewDraft(1, "reviewed-retry.jpg");
		String storageKey = imageStorage.store(new byte[] {(byte) 0xff, (byte) 0xd8, 1, 2});
		jdbcTemplate.update("UPDATE training_import_source_images SET storage_key = ? WHERE import_id = ?",
				storageKey, trainingImport.getId());
		jdbcTemplate.update("""
				UPDATE training_import_draft_athlete_rows
				SET recognition_metadata = recognition_metadata || '{"manuallyReviewed":true}'::jsonb
				WHERE draft_id = (SELECT id FROM training_import_drafts WHERE import_id = ?)
				""", trainingImport.getId());
		var before = draftService.getDraft(trainingImport.getId());
		trainingImport.changeStatus(TrainingImportStatus.FAILED);
		trainingImportRepository.saveAndFlush(trainingImport);
		when(geminiClient.recognize(any())).thenReturn(
				new GeminiTrainingRecognitionClient.GeminiRecognitionResponse("raw", validRecognition("Replacement")));
		Long attemptId = processingStateService.claimRetry(trainingImport.getId());
		processingService.processClaimedAttempt(attemptId);
		assertEquals(TrainingImportAttemptStatus.FAILED, attemptRepository.findById(attemptId).orElseThrow().getStatus());
		assertEquals("DRAFT_ALREADY_REVIEWED", attemptRepository.findById(attemptId).orElseThrow().getFailureCode());
		assertEquals(before.name(), draftRepository.findByTrainingImport_Id(trainingImport.getId()).orElseThrow().getName());
		assertEquals(1L, jdbcTemplate.queryForObject(
				"SELECT athlete_id FROM training_import_draft_athlete_rows WHERE draft_id = "
						+ "(SELECT id FROM training_import_drafts WHERE import_id = ?)", Long.class, trainingImport.getId()));
	}

	@Test
	void duplicatePendingClaimDoesNotCreateAnotherAttemptOrCallGemini() {
		TrainingImport trainingImport = saveImportWithStoredImage(
				1, "duplicate-claim.jpg", "1".repeat(64), new byte[] {(byte) 0xff, (byte) 0xd8, 1, 2});

		Long firstAttemptId = processingStateService.claimPending(trainingImport.getId());
		Long duplicateAttemptId = processingStateService.claimPending(trainingImport.getId());

		assertNotNull(firstAttemptId);
		assertNull(duplicateAttemptId);
		assertEquals(1L, count("training_import_attempts"));
		verify(geminiClient, never()).recognize(any());
	}

	@Test
	void pendingUploadIsProcessedAsynchronouslyByTheQueuePoller() throws Exception {
		trainingImportProperties.setProcessingEnabled(true);
		CountDownLatch recognitionStarted = new CountDownLatch(1);
		when(geminiClient.recognize(any())).thenAnswer(invocation -> {
			recognitionStarted.countDown();
			return new GeminiTrainingRecognitionClient.GeminiRecognitionResponse(
					"{\"provider\":\"async\"}", validRecognition("Asynchronous session"));
		});

		TrainingImportUploadResponse upload = uploadService.upload(1, createUpload("async.png"));

		assertEquals(TrainingImportStatus.PENDING, upload.status());
		verify(geminiClient, never()).recognize(any());
		processingService.scheduleNextPendingImport();
		assertTrue(recognitionStarted.await(5, TimeUnit.SECONDS));

		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
		TrainingImportStatus status = TrainingImportStatus.PROCESSING;
		while (status == TrainingImportStatus.PROCESSING && System.nanoTime() < deadline) {
			status = trainingImportRepository.findById(upload.id()).orElseThrow().getStatus();
			if (status == TrainingImportStatus.PROCESSING) {
				Thread.sleep(25);
			}
		}

		assertEquals(TrainingImportStatus.REVIEW_REQUIRED, status);
		assertEquals("Asynchronous session",
				draftRepository.findByTrainingImport_Id(upload.id()).orElseThrow().getName());
		assertEquals(0L, count("training_sessions"));
		verify(geminiClient, times(1)).recognize(any());
		trainingImportProperties.setProcessingEnabled(false);
	}

	private String validRecognition(String trainingName) {
		return """
				{
				  "name":{"value":"%s","originalText":"%s","uncertain":false,"uncertaintyReason":null},
				  "trainingDate":{"value":"2026-11-10","originalText":"10.11.2026","uncertain":false,"uncertaintyReason":null},
				  "recognizedGroupName":"Group A",
				  "definitions":[{
				    "sequenceNumber":1,
				    "name":{"value":"Main set","originalText":"Main set","uncertain":false,"uncertaintyReason":null},
				    "segments":[{
				      "sequenceNumber":1,
				      "distanceM":{"value":"400","originalText":"400 m","uncertain":false,"uncertaintyReason":null},
				      "restAfterSeconds":{"value":"60","originalText":"60 s","uncertain":false,"uncertaintyReason":null}
				    }],
				    "athleteRows":[{
				      "recognizedName":"Ada Lovelace","firstName":"Ada","lastName":"Lovelace",
				      "uncertain":false,"uncertaintyReason":null,
				      "executions":[{
				        "segmentSequenceNumber":1,"recognizedText":"12.3","status":"RECORDED",
				        "measuredResult":"12.3","usesSpikes":true,"uncertain":false,"uncertaintyReason":null
				      }]
				    }]
				  }]
				}
				""".formatted(trainingName, trainingName);
	}

	private TrainingImport saveImportWithImage(String filename, String hash) {
		return saveImportWithImage(1, filename, hash);
	}

	private TrainingImport saveImportWithImage(long groupId, String filename, String hash) {
		boolean possibleDuplicate = sourceImageRepository.existsBySha256(hash);
		TrainingImport trainingImport = trainingImportRepository.saveAndFlush(new TrainingImport(
				trainingGroupRepository.findById(groupId).orElseThrow(),
				userRepository.findById(1L).orElseThrow(),
				OffsetDateTime.now(ZoneOffset.UTC),
				possibleDuplicate));
		sourceImageRepository.saveAndFlush(new TrainingImportSourceImage(
				trainingImport, "generated-key-" + filename, filename, 120, 800, 600, hash));
		return trainingImport;
	}

	private TrainingImport saveReviewDraft(long groupId, String filename) {
		return persistReviewDraft(saveImportWithImage(groupId, filename, "b".repeat(64)));
	}

	private TrainingImport saveReviewDraftWithStoredImage(
			long groupId, String filename, byte[] contents) {
		return persistReviewDraft(saveImportWithStoredImage(
				groupId, filename, "d".repeat(64), contents));
	}

	private TrainingImport persistReviewDraft(TrainingImport trainingImport) {
		trainingImport.changeStatus(TrainingImportStatus.REVIEW_REQUIRED);
		trainingImportRepository.saveAndFlush(trainingImport);

		User admin = userRepository.findById(1L).orElseThrow();
		OffsetDateTime started = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1);
		TrainingImportAttempt attempt = attemptRepository.saveAndFlush(new TrainingImportAttempt(
				trainingImport,
				1,
				admin,
				"gemini-flash-lite-3.5",
				TrainingImportAttemptStatus.SUCCEEDED,
				started,
				OffsetDateTime.now(ZoneOffset.UTC),
				"{\"trainingName\":\"Intervals?\"}",
				null,
				null));
		TrainingImportDraft draft = draftRepository.saveAndFlush(new TrainingImportDraft(
				trainingImport,
				attempt,
				"Intervals",
				LocalDate.parse("2026-11-10"),
				Map.of("uncertain", true, "originalText", "Intervals?", "manuallyReviewed", false)));
		TrainingImportDraftDefinition definition = definitionRepository.saveAndFlush(
				new TrainingImportDraftDefinition(draft, "Main set", 1));
		TrainingImportDraftSegment firstSegment = segmentRepository.saveAndFlush(
				new TrainingImportDraftSegment(
						draft, definition, 1, 400, 60,
						Map.of("uncertain", true, "originalText", "400?", "manuallyReviewed", false)));
		TrainingImportDraftSegment secondSegment = segmentRepository.saveAndFlush(
				new TrainingImportDraftSegment(draft, definition, 2, 200, null, Map.of()));
		Athlete athlete = athleteRepository.findById(
				trainingImport.getTrainingGroup().getId() == 1 ? 1L : 2L).orElseThrow();
		TrainingImportDraftAthleteRow row = athleteRowRepository.saveAndFlush(
				new TrainingImportDraftAthleteRow(
						draft,
						definition,
						athlete,
						athlete.getFirstName() + " " + athlete.getLastName(),
						TrainingImportAthleteMatchStatus.MATCHED,
						Map.of("uncertain", true, "originalText", athlete.getFirstName(), "manuallyReviewed", false)));
		executionRepository.saveAllAndFlush(List.of(
				new TrainingImportDraftExecution(
						draft,
						definition,
						row,
						firstSegment,
						TrainingExecutionStatus.RECORDED,
						new BigDecimal("12.300"),
						false,
						"12.3?",
						Map.of("uncertain", true, "originalText", "12.3?", "manuallyReviewed", false)),
				new TrainingImportDraftExecution(
						draft,
						definition,
						row,
						secondSegment,
						TrainingExecutionStatus.PENDING,
						null,
						false,
						"",
						Map.of())));
		return trainingImport;
	}

	private TrainingImport saveImportWithStoredImage(
			long groupId,
			String filename,
			String hash,
			byte[] contents) {
		TrainingImport trainingImport = trainingImportRepository.saveAndFlush(new TrainingImport(
				trainingGroupRepository.findById(groupId).orElseThrow(),
				userRepository.findById(1L).orElseThrow(),
				OffsetDateTime.now(ZoneOffset.UTC),
				false));
		String storageKey = imageStorage.store(contents);
		sourceImageRepository.saveAndFlush(new TrainingImportSourceImage(
				trainingImport, storageKey, filename, contents.length, 2, 2, hash));
		return trainingImport;
	}

	private TrainingImportDraftUpdateRequest updateRequest(
			TrainingImportDraftResponse response,
			long athleteId,
			boolean skipAthlete) {
		TrainingImportDraftResponse.Definition current = response.definitions().getFirst();
		TrainingImportDraftResponse.AthleteRow currentRow = current.athleteRows().getFirst();
		List<TrainingImportDraftUpdateRequest.Segment> segments = current.segments().stream()
				.map(segment -> new TrainingImportDraftUpdateRequest.Segment(
						segment.id(),
						segment.sequenceNumber() == 1 ? 2 : 1,
						segment.sequenceNumber() == 1 ? 600 : segment.distanceM(),
						segment.restAfterSeconds(),
						segment.sequenceNumber() == 1))
				.toList();
		List<TrainingImportDraftUpdateRequest.Execution> executions =
				currentRow.executions().stream()
						.map(execution -> new TrainingImportDraftUpdateRequest.Execution(
								execution.id(),
								execution.segmentSequenceNumber() == 1 ? 2 : 1,
								execution.segmentSequenceNumber() == 1
										? TrainingExecutionStatus.RECORDED
										: TrainingExecutionStatus.NOT_COMPLETED,
								execution.segmentSequenceNumber() == 1
										? new BigDecimal("12.340")
										: null,
								execution.segmentSequenceNumber() == 1,
								execution.segmentSequenceNumber() == 1))
						.toList();
		return new TrainingImportDraftUpdateRequest(
				"Corrected intervals",
				LocalDate.parse("2026-11-11"),
				true,
				List.of(new TrainingImportDraftUpdateRequest.Definition(
						current.id(),
						"Updated main set",
						current.sequenceNumber(),
						segments,
						List.of(new TrainingImportDraftUpdateRequest.AthleteRow(
								currentRow.id(),
								currentRow.recognizedName(),
								skipAthlete ? null : athleteId,
								skipAthlete ? TrainingImportAthleteMatchStatus.SKIPPED
										: TrainingImportAthleteMatchStatus.MATCHED,
								executions)))));
	}

	private List<Byte> toByteList(byte[] bytes) {
		List<Byte> result = new java.util.ArrayList<>();
		for (byte value : bytes) {
			result.add(value);
		}
		return result;
	}

	private TrainingImportUploadResponse runInTransaction(
			TrainingImportUploadService service,
			MockMultipartFile upload) {
		return new TransactionTemplate(transactionManager)
				.execute(status -> service.upload(1, upload));
	}

	private TrainingImportUploadService newUploadService(
			TrainingImportSourceImageRepository sourceRepository,
			TrainingImportImageStorage storage) {
		return new TrainingImportUploadService(
				trainingImportRepository,
				sourceRepository,
				imageProcessor,
				storage,
				trainingGroupService,
				accountManagementService,
				accessScopeService);
	}

	private TrainingImportProperties propertiesWithStoragePath(Path path) {
		TrainingImportProperties properties = new TrainingImportProperties();
		properties.setStoragePath(path);
		return properties;
	}

	private MockMultipartFile createUpload(String filename) throws IOException {
		BufferedImage image = new BufferedImage(320, 200, BufferedImage.TYPE_INT_RGB);
		Graphics2D graphics = image.createGraphics();
		try {
			graphics.setColor(Color.WHITE);
			graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
			graphics.setColor(Color.BLACK);
			graphics.drawLine(10, 10, 300, 180);
		} finally {
			graphics.dispose();
		}
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		ImageIO.write(image, "png", bytes);
		return new MockMultipartFile("image", filename, "image/png", bytes.toByteArray());
	}

	private void setAuthenticatedUser(long userId, UserRole role) {
		SecurityContextHolder.getContext().setAuthentication(
				UsernamePasswordAuthenticationToken.authenticated(
						new AuthenticatedUser(userId, "test-user", "", role, true), null, List.of()));
	}

	private long count(String tableName) {
		return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName, Long.class);
	}

	private void clearImageDirectory(Path directory) throws IOException {
		if (!Files.exists(directory)) {
			Files.createDirectories(directory);
			return;
		}
		try (var paths = Files.walk(directory)) {
			for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
				if (!path.equals(directory)) {
					Files.deleteIfExists(path);
				}
			}
		}
	}
}

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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import javax.imageio.ImageIO;
import pl.tfhub.api.common.authorization.AccessScopeService;
import pl.tfhub.api.athlete.entity.Athlete;
import pl.tfhub.api.athlete.repository.AthleteRepository;
import pl.tfhub.api.training.entity.Training;
import pl.tfhub.api.training.enums.TrainingExecutionStatus;
import pl.tfhub.api.training.repository.TrainingRepository;
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
import pl.tfhub.api.trainingimport.service.TrainingImportImageProcessor;
import pl.tfhub.api.trainingimport.service.TrainingImportImageStorage;
import pl.tfhub.api.trainingimport.service.TrainingImportSourceImageService;
import pl.tfhub.api.trainingimport.service.TrainingImportUploadService;
import pl.tfhub.api.user.entity.User;
import pl.tfhub.api.user.enums.UserRole;
import pl.tfhub.api.user.repository.UserRepository;
import pl.tfhub.api.user.service.AccountManagementService;
import pl.tfhub.api.user.service.AuthenticatedUser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
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
	private TrainingGroupRepository trainingGroupRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private TrainingRepository trainingRepository;

	@Autowired
	private TrainingImportUploadService uploadService;

	@Autowired
	private TrainingImportDraftService draftService;

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
		TrainingImport trainingImport = saveImportWithImage(groupId, filename, "b".repeat(64));
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
		Athlete athlete = athleteRepository.findById(groupId == 1 ? 1L : 2L).orElseThrow();
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

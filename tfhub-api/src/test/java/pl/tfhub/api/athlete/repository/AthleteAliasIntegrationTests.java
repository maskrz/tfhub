package pl.tfhub.api.athlete.repository;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import pl.tfhub.api.athlete.dto.request.ChangeAthleteStatusRequest;
import pl.tfhub.api.athlete.dto.request.CreateAthleteRequest;
import pl.tfhub.api.athlete.dto.request.UpdateAthleteRequest;
import pl.tfhub.api.athlete.enums.Gender;
import pl.tfhub.api.athlete.exception.AthleteAliasConflictException;
import pl.tfhub.api.athlete.service.AthleteService;
import pl.tfhub.api.common.authorization.AccessScopeService;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Testcontainers
class AthleteAliasIntegrationTests {

	@Container
	private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

	@DynamicPropertySource
	static void configureDatabase(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		registry.add("spring.datasource.username", POSTGRES::getUsername);
		registry.add("spring.datasource.password", POSTGRES::getPassword);
	}

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private AthleteService athleteService;

	@MockitoBean
	private AccessScopeService accessScopeService;

	@Test
	void athleteServicePreservesOmittedAliasesAndRejectsConflictingReactivation() {
		long group = group();
		String firstName = UUID.randomUUID().toString();
		var first = athleteService.createAthlete(new CreateAthleteRequest(
				firstName, "Test", 2010, Gender.MALE, group, List.of("Marcin K.", "kowal")));
		assertEquals(List.of("Marcin K.", "kowal"), first.aliases());
		var unchanged = athleteService.updateAthlete(first.id(),
				new UpdateAthleteRequest(firstName, "Test", 2010, Gender.MALE, group));
		assertEquals(first.aliases(), unchanged.aliases());
		athleteService.changeAthleteStatus(first.id(), new ChangeAthleteStatusRequest(false));
		var second = athleteService.createAthlete(new CreateAthleteRequest(
				UUID.randomUUID().toString(), "Test", 2010, Gender.MALE, group, List.of("MARCIN K")));
		assertThrows(AthleteAliasConflictException.class,
				() -> athleteService.changeAthleteStatus(first.id(), new ChangeAthleteStatusRequest(true)));
		assertEquals(false, athleteService.getAthlete(first.id()).active());
		assertEquals(first.aliases(), athleteService.getAthlete(first.id()).aliases());
		athleteService.updateAthlete(second.id(), new UpdateAthleteRequest(
				second.firstName(), second.lastName(), second.birthYear(), second.gender(), group, List.of()));
		assertEquals(true, athleteService.changeAthleteStatus(first.id(), new ChangeAthleteStatusRequest(true)).active());
	}

	@Test
	void createAndMoveConflictsRollBackAllChanges() {
		long firstGroup = group();
		long secondGroup = group();
		var first = athleteService.createAthlete(new CreateAthleteRequest(
				UUID.randomUUID().toString(), "Test", 2010, Gender.MALE, firstGroup, List.of("alias")));
		var second = athleteService.createAthlete(new CreateAthleteRequest(
				UUID.randomUUID().toString(), "Test", 2010, Gender.MALE, secondGroup, List.of("ALIAS")));
		assertThrows(AthleteAliasConflictException.class, () -> athleteService.createAthlete(
				new CreateAthleteRequest(UUID.randomUUID().toString(), "Test", 2010,
						Gender.MALE, firstGroup, List.of(" ALIAS "))));
		assertThrows(AthleteAliasConflictException.class, () -> athleteService.updateAthlete(second.id(),
				new UpdateAthleteRequest("Changed", second.lastName(), second.birthYear(), second.gender(), firstGroup)));
		var unchanged = athleteService.getAthlete(second.id());
		assertEquals(second.firstName(), unchanged.firstName());
		assertEquals(secondGroup, unchanged.trainingGroupId());
		assertEquals(List.of("ALIAS"), unchanged.aliases());
		assertEquals(List.of("alias"), athleteService.getAthlete(first.id()).aliases());
	}

	@Test
	void importCandidatesIncludeOnlyActiveAthletesInTheSelectedGroup() {
		long group = group();
		long otherGroup = group();
		var active = athleteService.createAthlete(new CreateAthleteRequest(
				UUID.randomUUID().toString(), "Test", 2010, Gender.MALE, group, List.of("Marcin K.")));
		var inactive = athleteService.createAthlete(new CreateAthleteRequest(
				UUID.randomUUID().toString(), "Test", 2010, Gender.MALE, group, List.of("inactive")));
		athleteService.changeAthleteStatus(inactive.id(), new ChangeAthleteStatusRequest(false));
		athleteService.createAthlete(new CreateAthleteRequest(
				UUID.randomUUID().toString(), "Test", 2010, Gender.MALE, otherGroup, List.of("other")));
		var candidates = athleteService.findImportCandidates(group);
		assertEquals(List.of(active.id()), candidates.stream().map(AthleteService.ImportCandidate::id).toList());
		assertTrue(candidates.getFirst().normalizedAliases().contains(athleteService.normalizeImportName(" MARCIN K ")));
	}

	@Test
	void duplicateAliasesAreRejectedOnCreateAndEditButEquivalentSelfEditIsAllowed() {
		long group = group();
		assertThrows(AthleteAliasConflictException.class, () -> athleteService.createAthlete(
				new CreateAthleteRequest(UUID.randomUUID().toString(), "Test", 2010,
						Gender.MALE, group, List.of(" Marcin  K. ", "marcin k"))));
		var first = athleteService.createAthlete(new CreateAthleteRequest(
				UUID.randomUUID().toString(), "Test", 2010, Gender.MALE, group, List.of("Marcin K.")));
		var second = athleteService.createAthlete(new CreateAthleteRequest(
				UUID.randomUUID().toString(), "Test", 2010, Gender.MALE, group, List.of("kowal")));
		var edited = athleteService.updateAthlete(first.id(), new UpdateAthleteRequest(
				first.firstName(), first.lastName(), first.birthYear(), first.gender(), group, List.of("MARCIN K")));
		assertEquals(List.of("MARCIN K"), edited.aliases());
		assertThrows(AthleteAliasConflictException.class, () -> athleteService.updateAthlete(second.id(),
				new UpdateAthleteRequest(second.firstName(), second.lastName(), second.birthYear(),
						second.gender(), group, List.of("marcin k."))));
		assertEquals(List.of("kowal"), athleteService.getAthlete(second.id()).aliases());
	}

	@Test
	void changingGroupAndAliasesChecksTheFinalState() {
		long source = group();
		long destination = group();
		athleteService.createAthlete(new CreateAthleteRequest(
				UUID.randomUUID().toString(), "Test", 2010, Gender.MALE, destination, List.of("conflict")));
		var athlete = athleteService.createAthlete(new CreateAthleteRequest(
				UUID.randomUUID().toString(), "Test", 2010, Gender.MALE, source, List.of("conflict")));
		var moved = athleteService.updateAthlete(athlete.id(), new UpdateAthleteRequest(
				athlete.firstName(), athlete.lastName(), athlete.birthYear(), athlete.gender(),
				destination, List.of("new alias")));
		assertEquals(destination, moved.trainingGroupId());
		assertEquals(List.of("new alias"), moved.aliases());
	}

	@Test
	void ungroupedAthletesMayShareAliasesButCannotJoinAConflictingGroup() {
		long group = group();
		var first = athleteService.createAthlete(new CreateAthleteRequest(
				UUID.randomUUID().toString(), "Test", 2010, Gender.MALE, null, List.of("alias")));
		var second = athleteService.createAthlete(new CreateAthleteRequest(
				UUID.randomUUID().toString(), "Test", 2010, Gender.MALE, null, List.of("alias")));
		athleteService.updateAthlete(first.id(), new UpdateAthleteRequest(
				first.firstName(), first.lastName(), first.birthYear(), first.gender(), group));
		assertThrows(AthleteAliasConflictException.class, () -> athleteService.updateAthlete(second.id(),
				new UpdateAthleteRequest(second.firstName(), second.lastName(), second.birthYear(), second.gender(), group)));
		assertEquals(null, athleteService.getAthlete(second.id()).trainingGroupId());
		assertEquals(List.of("alias"), athleteService.getAthlete(second.id()).aliases());
	}

	@Test
	void canonicalizationPreservesDiacriticsAndOnlyRemovesInitialPeriods() {
		assertEquals(normalize("  MARCIN\u00a0  K. "), normalize("marcin k"));
		assertEquals(normalize("A\u0301nia"), normalize("ÁNIA"));
		assertEquals(normalize("ŁUKASZ"), normalize("łukasz"));
		assertNotEquals(normalize("Łukasz"), normalize("Lukasz"));
		assertNotEquals(normalize("kowal."), normalize("kowal"));
		assertNotEquals(normalize("Jan-K"), normalize("Jan K"));
		assertEquals(normalize("A. B."), normalize("a b"));
	}

	@Test
	void databaseEnforcesActiveGroupScopeAndReactivation() {
		long group = group();
		long otherGroup = group();
		long first = athlete(group, true);
		long second = athlete(group, false);
		long other = athlete(otherGroup, true);
		insertAlias(first, "Marcin K.");
		insertAlias(second, " MARCIN k ");
		insertAlias(other, "marcin k");
		assertThrows(DataIntegrityViolationException.class,
				() -> jdbc.update("UPDATE athletes SET active = true WHERE id = ?", second));
		assertEquals(Boolean.FALSE, jdbc.queryForObject("SELECT active FROM athletes WHERE id = ?", Boolean.class, second));
		jdbc.update("DELETE FROM athlete_aliases WHERE athlete_id = ?", first);
		jdbc.update("UPDATE athletes SET active = true WHERE id = ?", second);
		assertThrows(DataIntegrityViolationException.class, () -> insertAlias(second, "marcin K."));
	}

	@Test
	void parentScopeCannotBeSpoofedAndGroupChangesAreChecked() {
		long group = group();
		long otherGroup = group();
		long first = athlete(group, true);
		long second = athlete(otherGroup, true);
		insertAlias(first, "kowal");
		jdbc.update("INSERT INTO athlete_aliases (athlete_id, alias, training_group_id, active) VALUES (?, ?, ?, false)",
				second, "KOWAL", group);
		assertEquals(otherGroup, jdbc.queryForObject(
				"SELECT training_group_id FROM athlete_aliases WHERE athlete_id = ?", Long.class, second));
		assertThrows(DataIntegrityViolationException.class,
				() -> jdbc.update("UPDATE athletes SET training_group_id = ? WHERE id = ?", group, second));
	}

	@Test
	void concurrentConflictingWritesHaveExactlyOneWinner() throws Exception {
		long group = group();
		long first = athlete(group, true);
		long second = athlete(group, true);
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		try (var pool = Executors.newFixedThreadPool(2)) {
			var a = pool.submit(() -> tryInsert(first, ready, start));
			var b = pool.submit(() -> tryInsert(second, ready, start));
			assertTrue(ready.await(10, TimeUnit.SECONDS));
			start.countDown();
			assertEquals(1, (a.get(10, TimeUnit.SECONDS) ? 1 : 0) + (b.get(10, TimeUnit.SECONDS) ? 1 : 0));
		}
	}

	@Test
	void concurrentActivationAndAliasCreationHaveExactlyOneWinner() throws Exception {
		long group = group();
		long inactive = athlete(group, false);
		long active = athlete(group, true);
		insertAlias(inactive, "same");
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		try (var pool = Executors.newFixedThreadPool(2)) {
			var activation = pool.submit(() -> tryWrite(
					() -> jdbc.update("UPDATE athletes SET active = true WHERE id = ?", inactive), ready, start));
			var insertion = pool.submit(() -> tryWrite(() -> insertAlias(active, "SAME"), ready, start));
			assertTrue(ready.await(10, TimeUnit.SECONDS));
			start.countDown();
			assertEquals(1, (activation.get(10, TimeUnit.SECONDS) ? 1 : 0)
					+ (insertion.get(10, TimeUnit.SECONDS) ? 1 : 0));
		}
	}

	private boolean tryInsert(long athlete, CountDownLatch ready, CountDownLatch start) throws InterruptedException {
		return tryWrite(() -> insertAlias(athlete, "concurrent"), ready, start);
	}

	private boolean tryWrite(Runnable write, CountDownLatch ready, CountDownLatch start) throws InterruptedException {
		ready.countDown();
		if (!start.await(10, TimeUnit.SECONDS)) {
			throw new IllegalStateException("Concurrent test did not start");
		}
		try {
			write.run();
			return true;
		} catch (DataIntegrityViolationException conflict) {
			return false;
		}
	}

	private String normalize(String value) {
		return jdbc.queryForObject("SELECT normalize_athlete_alias(?)", String.class, value);
	}

	private long group() {
		return jdbc.queryForObject("INSERT INTO training_groups(name) VALUES (?) RETURNING id",
				Long.class, UUID.randomUUID().toString());
	}

	private long athlete(Long group, boolean active) {
		return jdbc.queryForObject("""
				INSERT INTO athletes(first_name, last_name, birth_year, gender, training_group_id, active)
				VALUES (?, 'Test', 2010, 'MALE', ?, ?) RETURNING id
				""", Long.class, UUID.randomUUID().toString(), group, active);
	}

	private void insertAlias(long athlete, String alias) {
		jdbc.update("INSERT INTO athlete_aliases (athlete_id, alias) VALUES (?, ?)", athlete, alias);
	}
}

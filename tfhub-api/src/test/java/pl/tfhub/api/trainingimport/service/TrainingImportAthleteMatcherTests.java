package pl.tfhub.api.trainingimport.service;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.tfhub.api.athlete.service.AthleteService;
import pl.tfhub.api.athlete.service.AthleteService.ImportCandidate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TrainingImportAthleteMatcherTests {

	private final AthleteService athleteService = mock(AthleteService.class);
	private final TrainingImportAthleteMatcher matcher = new TrainingImportAthleteMatcher(athleteService);
	private final ImportCandidate marcin = candidate(1, "Marcin", "Kowalski", "marcin", "marcin k", "kowal");
	private final ImportCandidate anna = candidate(2, "Anna", "Nowak", "ania");

	@BeforeEach
	void normalizeCanonicalFixtures() {
		when(athleteService.normalizeImportName(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
	}

	@Test
	void exactAliasesAndFullNamesIdentifyTheSameAthlete() {
		for (String name : List.of("marcin", "marcin k", "kowal", "marcin kowalski")) {
			var match = matcher.match(row(name, null, null, false), List.of(marcin, anna));
			assertEquals(1L, match.athleteId(), name);
			assertNull(match.reason(), name);
		}
	}

	@Test
	void uniqueFirstNameWithoutAliasDoesNotMatch() {
		var noAliases = candidate(1, "Marcin", "Kowalski");
		var match = matcher.match(row("marcin", "marcin", null, false), List.of(noAliases));
		assertNull(match.athleteId());
		assertEquals("No athlete matched the recognized name", match.reason());
	}

	@Test
	void conflictingAliasAndFullNameRemainUnresolved() {
		var other = candidate(2, "Anna", "Nowak", "marcin kowalski");
		var match = matcher.match(row("marcin kowalski", "marcin", "kowalski", false), List.of(marcin, other));
		assertNull(match.athleteId());
		assertEquals("The recognized name is ambiguous", match.reason());
	}

	@Test
	void duplicateAliasCandidatesRemainUnresolvedInEitherOrder() {
		var duplicate = candidate(2, "Anna", "Nowak", "kowal");
		for (List<ImportCandidate> candidates : List.of(List.of(marcin, duplicate), List.of(duplicate, marcin))) {
			var match = matcher.match(row("kowal", null, null, false), candidates);
			assertNull(match.athleteId());
			assertEquals("The recognized name is ambiguous", match.reason());
		}
	}

	@Test
	void uncertainRecognitionNeverAutomaticallyAssignsAnExactAlias() {
		var match = matcher.match(row("kowal", null, null, true), List.of(marcin));
		assertNull(match.athleteId());
		assertEquals("The recognized athlete name is uncertain", match.reason());
	}

	@Test
	void uncertainRecognitionCanOfferOnlyAnExplicitManualSuggestion() {
		var match = matcher.match(row("marcin kowalska", "marcin", "kowalska", true), List.of(marcin));
		assertNull(match.athleteId());
		assertEquals("The recognized athlete name is uncertain", match.reason());
		assertEquals(List.of(1L), match.suggestionIds());
	}

	@Test
	void sameFirstNameWithoutAnExplicitAliasRemainsUnresolved() {
		var other = candidate(3, "Marcin", "Nowak", "nowak");
		var match = matcher.match(row("marcin", "marcin", null, false),
				List.of(candidate(1, "Marcin", "Kowalski"), other));
		assertNull(match.athleteId());
		assertEquals("No athlete matched the recognized name", match.reason());
	}

	@Test
	void approximateSurnameAndPartialNamesAreOnlySuggestions() {
		var match = matcher.match(row("marcin kowalska", "marcin", "kowalska", false), List.of(marcin));
		assertNull(match.athleteId());
		assertEquals(List.of(1L), match.suggestionIds());
		assertNull(matcher.match(row("marcin kowa", "marcin", "kowa", false), List.of(marcin)).athleteId());
		assertNull(matcher.match(row("kowall", null, null, false), List.of(marcin)).athleteId());
	}

	private ImportCandidate candidate(long id, String first, String last, String... aliases) {
		return new ImportCandidate(id, first, last,
				(first + " " + last).toLowerCase(java.util.Locale.ROOT), List.of(aliases));
	}

	private TrainingImportRecognitionData.AthleteRow row(
			String name, String first, String last, boolean uncertain) {
		return new TrainingImportRecognitionData.AthleteRow(name, first, last, uncertain, null, List.of());
	}
}

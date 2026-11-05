package pl.tfhub.api.athlete;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;
import pl.tfhub.api.athlete.dto.AthleteRequest;
import pl.tfhub.api.athlete.dto.AthleteResponse;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.SQLException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AthleteServiceTests {

	@Mock
	private AthleteRepository athleteRepository;

	@InjectMocks
	private AthleteService athleteService;

	@Test
	void retrievesAthleteAndMapsItToResponseDto() {
		Athlete athlete = new Athlete("Anna", "Nowak", 2008, Gender.FEMALE);
		ReflectionTestUtils.setField(athlete, "id", 7L);
		when(athleteRepository.findById(7L)).thenReturn(Optional.of(athlete));

		AthleteResponse response = athleteService.getAthlete(7L);

		assertEquals(new AthleteResponse(7L, "Anna", "Nowak", 2008, Gender.FEMALE, true), response);
		verify(athleteRepository).findById(7L);
	}

	@Test
	void throwsNotFoundWhenAthleteDoesNotExist() {
		when(athleteRepository.findById(99L)).thenReturn(Optional.empty());

		assertThrows(AthleteNotFoundException.class, () -> athleteService.getAthlete(99L));
	}

	@Test
	void createsAndPersistsAnActiveAthlete() {
		AthleteRequest request = new AthleteRequest("John", "Smith", 2010, Gender.MALE);
		when(athleteRepository.saveAndFlush(org.mockito.ArgumentMatchers.any(Athlete.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));

		AthleteResponse response = athleteService.createAthlete(request);

		ArgumentCaptor<Athlete> athleteCaptor = ArgumentCaptor.forClass(Athlete.class);
		verify(athleteRepository).saveAndFlush(athleteCaptor.capture());
		Athlete persisted = athleteCaptor.getValue();
		assertEquals("John", persisted.getFirstName());
		assertEquals("Smith", persisted.getLastName());
		assertEquals(2010, persisted.getBirthYear());
		assertEquals(Gender.MALE, persisted.getGender());
		assertTrue(persisted.isActive());
		assertEquals("John", response.firstName());
	}

	@Test
	void rejectsAnExistingAthleteBeforeSaving() {
		when(athleteRepository.existsByFirstNameAndLastNameAndBirthYear("John", "Smith", 2010))
				.thenReturn(true);

		assertThrows(AthleteAlreadyExistsException.class, () -> athleteService.createAthlete(
				new AthleteRequest("John", "Smith", 2010, Gender.MALE)));

		verify(athleteRepository, never()).saveAndFlush(org.mockito.ArgumentMatchers.any(Athlete.class));
	}

	@Test
	void translatesConcurrentUniqueConstraintViolationToDuplicateConflict() {
		ConstraintViolationException constraintViolation = new ConstraintViolationException(
				"duplicate athlete", new SQLException("duplicate"), "uq_athletes_name_birth_year");
		when(athleteRepository.saveAndFlush(org.mockito.ArgumentMatchers.any(Athlete.class)))
				.thenThrow(new DataIntegrityViolationException("unique constraint", constraintViolation));

		assertThrows(AthleteAlreadyExistsException.class, () -> athleteService.createAthlete(
				new AthleteRequest("John", "Smith", 2010, Gender.MALE)));
	}

	@Test
	void doesNotTranslateUnrelatedDatabaseConstraintViolations() {
		ConstraintViolationException constraintViolation = new ConstraintViolationException(
				"other constraint", new SQLException("failure"), "some_other_constraint");
		when(athleteRepository.saveAndFlush(org.mockito.ArgumentMatchers.any(Athlete.class)))
				.thenThrow(new DataIntegrityViolationException("constraint", constraintViolation));

		assertThrows(DataIntegrityViolationException.class, () -> athleteService.createAthlete(
				new AthleteRequest("John", "Smith", 2010, Gender.MALE)));
	}
}

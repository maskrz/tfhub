package pl.tfhub.api.athlete.service;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;
import pl.tfhub.api.athlete.dto.request.ChangeAthleteStatusRequest;
import pl.tfhub.api.athlete.dto.request.CreateAthleteRequest;
import pl.tfhub.api.athlete.dto.request.SearchAthletesRequest;
import pl.tfhub.api.athlete.dto.request.UpdateAthleteRequest;
import pl.tfhub.api.athlete.dto.response.AthleteResponse;
import pl.tfhub.api.athlete.entity.Athlete;
import pl.tfhub.api.athlete.enums.Gender;
import pl.tfhub.api.athlete.exception.AthleteAlreadyExistsException;
import pl.tfhub.api.athlete.exception.AthleteNotFoundException;
import pl.tfhub.api.athlete.repository.AthleteRepository;
import pl.tfhub.api.common.authorization.AccessScopeService;
import pl.tfhub.api.traininggroup.entity.TrainingGroup;
import pl.tfhub.api.traininggroup.repository.TrainingGroupRepository;

import static org.mockito.Mockito.lenient;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AthleteServiceTests {

	@Mock
	private AthleteRepository athleteRepository;

	@Mock
	private TrainingGroupRepository trainingGroupRepository;

	@Mock
	private AccessScopeService accessScopeService;

	@InjectMocks
	private AthleteService athleteService;

	@org.junit.jupiter.api.BeforeEach
	void allowAdminScopeInUnitTests() {
		lenient().when(accessScopeService.isAdmin()).thenReturn(true);
	}

	@Test
	void searchesWithAllCriteriaAndMapsEntitiesToResponseDtos() {
		Athlete athlete = new Athlete("Jan", "Kowalski", 2010, Gender.MALE);
		ReflectionTestUtils.setField(athlete, "id", 1L);
		SearchAthletesRequest request = new SearchAthletesRequest("Jan", "Kow", true, Gender.MALE, null);
		when(athleteRepository.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of(athlete));

		List<AthleteResponse> responses = athleteService.searchAthletes(request);

		assertEquals(List.of(new AthleteResponse(1L, "Jan", "Kowalski", 2010, Gender.MALE, true)), responses);
		verify(athleteRepository).findAll(any(Specification.class), any(Sort.class));
	}

	@Test
	void sortsAthletesBySurnameThenFirstNameIgnoringCase() {
		when(athleteRepository.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of());

		athleteService.searchAthletes(new SearchAthletesRequest(null, null, null, null, null));

		ArgumentCaptor<Sort> sortCaptor = ArgumentCaptor.forClass(Sort.class);
		verify(athleteRepository).findAll(any(Specification.class), sortCaptor.capture());
		List<Sort.Order> orders = sortCaptor.getValue().toList();
		assertEquals(List.of("lastName", "firstName", "id"), orders.stream().map(Sort.Order::getProperty).toList());
		assertTrue(orders.stream().allMatch(Sort.Order::isAscending));
		assertTrue(orders.get(0).isIgnoreCase());
		assertTrue(orders.get(1).isIgnoreCase());
	}

	@Test
	void includesCurrentTrainingGroupIdInTheAthleteResponse() {
		TrainingGroup group = mock(TrainingGroup.class);
		when(group.getId()).thenReturn(7L);
		Athlete athlete = new Athlete("Jan", "Kowalski", 2010, Gender.MALE, group);
		ReflectionTestUtils.setField(athlete, "id", 1L);
		when(athleteRepository.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of(athlete));

		List<AthleteResponse> responses = athleteService.searchAthletes(
				new SearchAthletesRequest(null, null, null, null, null));

		assertEquals(7L, responses.getFirst().trainingGroupId());
	}

	@Test
	void searchesAllAthletesWhenCriteriaAreAbsent() {
		when(athleteRepository.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of());

		assertEquals(List.of(), athleteService.searchAthletes(
				new SearchAthletesRequest(null, null, null, null, null)));

		verify(athleteRepository).findAll(any(Specification.class), any(Sort.class));
	}

	@Test
	void searchSpecificationAddsExactBirthYearAndOmitsItWhenNotProvided() {
		when(athleteRepository.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of());
		athleteService.searchAthletes(new SearchAthletesRequest(null, null, null, null, 2010));

		ArgumentCaptor<Specification<Athlete>> specificationCaptor = ArgumentCaptor.forClass(Specification.class);
		verify(athleteRepository).findAll(specificationCaptor.capture(), any(Sort.class));
		Root<Athlete> root = mock(Root.class);
		CriteriaQuery<?> query = mock(CriteriaQuery.class);
		CriteriaBuilder criteriaBuilder = mock(CriteriaBuilder.class);
		Path<Object> birthYearPath = mock(Path.class);
		Predicate birthYearPredicate = mock(Predicate.class);
		when(root.get("birthYear")).thenReturn(birthYearPath);
		when(criteriaBuilder.equal(birthYearPath, 2010)).thenReturn(birthYearPredicate);
		when(criteriaBuilder.and(birthYearPredicate)).thenReturn(birthYearPredicate);

		specificationCaptor.getValue().toPredicate(root, query, criteriaBuilder);

		verify(criteriaBuilder).equal(birthYearPath, 2010);

		when(athleteRepository.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of());
		athleteService.searchAthletes(new SearchAthletesRequest(null, null, null, null, null));
		ArgumentCaptor<Specification<Athlete>> noBirthYearCaptor = ArgumentCaptor.forClass(Specification.class);
		verify(athleteRepository, org.mockito.Mockito.times(2)).findAll(noBirthYearCaptor.capture(), any(Sort.class));

		Root<Athlete> rootWithoutBirthYear = mock(Root.class);
		CriteriaBuilder criteriaBuilderWithoutBirthYear = mock(CriteriaBuilder.class);
		noBirthYearCaptor.getAllValues().get(1)
				.toPredicate(rootWithoutBirthYear, query, criteriaBuilderWithoutBirthYear);

		verify(rootWithoutBirthYear, never()).get("birthYear");
	}

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
	void updatesAllPersonalFieldsAndPreservesActiveStatus() {
		Athlete athlete = new Athlete("Old", "Name", 2000, Gender.MALE);
		ReflectionTestUtils.setField(athlete, "id", 12L);
		ReflectionTestUtils.setField(athlete, "active", false);
		when(athleteRepository.findById(12L)).thenReturn(Optional.of(athlete));
		when(athleteRepository.existsByFirstNameAndLastNameAndBirthYearAndIdNot(
				"New", "Name", 2010, 12L)).thenReturn(false);
		when(athleteRepository.saveAndFlush(athlete)).thenReturn(athlete);

		AthleteResponse response = athleteService.updateAthlete(
				12L, new UpdateAthleteRequest("New", "Name", 2010, Gender.FEMALE));

		assertEquals(new AthleteResponse(12L, "New", "Name", 2010, Gender.FEMALE, false), response);
		verify(athleteRepository).existsByFirstNameAndLastNameAndBirthYearAndIdNot(
				"New", "Name", 2010, 12L);
	}

	@Test
	void allowsUnchangedUniqueFieldsAndPreservesActiveStatus() {
		Athlete athlete = new Athlete("Jan", "Kowalski", 2010, Gender.MALE);
		ReflectionTestUtils.setField(athlete, "id", 12L);
		ReflectionTestUtils.setField(athlete, "active", true);
		when(athleteRepository.findById(12L)).thenReturn(Optional.of(athlete));
		when(athleteRepository.existsByFirstNameAndLastNameAndBirthYearAndIdNot(
				"Jan", "Kowalski", 2010, 12L)).thenReturn(false);
		when(athleteRepository.saveAndFlush(athlete)).thenReturn(athlete);

		AthleteResponse response = athleteService.updateAthlete(
				12L, new UpdateAthleteRequest("Jan", "Kowalski", 2010, Gender.MALE));

		assertEquals(new AthleteResponse(12L, "Jan", "Kowalski", 2010, Gender.MALE, true), response);
	}

	@Test
	void canUpdateNamesWhileKeepingBirthYearAndGenderUnchanged() {
		Athlete athlete = new Athlete("Old", "Name", 2010, Gender.FEMALE);
		ReflectionTestUtils.setField(athlete, "id", 12L);
		when(athleteRepository.findById(12L)).thenReturn(Optional.of(athlete));
		when(athleteRepository.existsByFirstNameAndLastNameAndBirthYearAndIdNot(
				"New", "Name", 2010, 12L)).thenReturn(false);
		when(athleteRepository.saveAndFlush(athlete)).thenReturn(athlete);

		AthleteResponse response = athleteService.updateAthlete(
				12L, new UpdateAthleteRequest("New", "Name", 2010, Gender.FEMALE));

		assertEquals(new AthleteResponse(12L, "New", "Name", 2010, Gender.FEMALE, true), response);
	}

	@Test
	void rejectsDuplicateAthleteOwnedByAnotherId() {
		Athlete athlete = new Athlete("Old", "Name", 2000, Gender.MALE);
		ReflectionTestUtils.setField(athlete, "id", 12L);
		when(athleteRepository.findById(12L)).thenReturn(Optional.of(athlete));
		when(athleteRepository.existsByFirstNameAndLastNameAndBirthYearAndIdNot(
				"Existing", "Athlete", 2010, 12L)).thenReturn(true);

		assertThrows(AthleteAlreadyExistsException.class, () -> athleteService.updateAthlete(
				12L, new UpdateAthleteRequest("Existing", "Athlete", 2010, Gender.FEMALE)));
		verify(athleteRepository, never()).saveAndFlush(org.mockito.ArgumentMatchers.any(Athlete.class));
	}

	@Test
	void returnsNotFoundWhenUpdatingUnknownAthlete() {
		when(athleteRepository.findById(99L)).thenReturn(Optional.empty());

		assertThrows(AthleteNotFoundException.class, () -> athleteService.updateAthlete(
				99L, new UpdateAthleteRequest("Jan", "Kowalski", 2010, Gender.MALE)));
	}

	@Test
	void changesStatusWithoutModifyingOtherAthleteFieldsOrDeletingRecords() {
		Athlete athlete = new Athlete("Anna", "Nowak", 2008, Gender.FEMALE);
		ReflectionTestUtils.setField(athlete, "id", 42L);
		when(athleteRepository.findById(42L)).thenReturn(Optional.of(athlete));
		when(athleteRepository.saveAndFlush(athlete)).thenReturn(athlete);

		AthleteResponse response = athleteService.changeAthleteStatus(
				42L, new ChangeAthleteStatusRequest(false));

		assertEquals(new AthleteResponse(42L, "Anna", "Nowak", 2008, Gender.FEMALE, false), response);
		assertEquals("Anna", athlete.getFirstName());
		assertEquals("Nowak", athlete.getLastName());
		assertEquals(2008, athlete.getBirthYear());
		assertEquals(Gender.FEMALE, athlete.getGender());
		verify(athleteRepository).saveAndFlush(athlete);
		verify(athleteRepository, never()).delete(athlete);
		verify(athleteRepository, never()).deleteById(42L);
	}

	@Test
	void allowsChangingStatusToTheCurrentValue() {
		Athlete athlete = new Athlete("Jan", "Kowalski", 2010, Gender.MALE);
		ReflectionTestUtils.setField(athlete, "id", 42L);
		when(athleteRepository.findById(42L)).thenReturn(Optional.of(athlete));
		when(athleteRepository.saveAndFlush(athlete)).thenReturn(athlete);

		assertEquals(new AthleteResponse(42L, "Jan", "Kowalski", 2010, Gender.MALE, true),
				athleteService.changeAthleteStatus(42L, new ChangeAthleteStatusRequest(true)));
	}

	@Test
	void activatesInactiveAthleteAndAllowsRepeatingDeactivation() {
		Athlete athlete = new Athlete("Anna", "Nowak", 2008, Gender.FEMALE);
		ReflectionTestUtils.setField(athlete, "id", 42L);
		ReflectionTestUtils.setField(athlete, "active", false);
		when(athleteRepository.findById(42L)).thenReturn(Optional.of(athlete));
		when(athleteRepository.saveAndFlush(athlete)).thenReturn(athlete);

		assertEquals(new AthleteResponse(42L, "Anna", "Nowak", 2008, Gender.FEMALE, true),
				athleteService.changeAthleteStatus(42L, new ChangeAthleteStatusRequest(true)));
		assertEquals(new AthleteResponse(42L, "Anna", "Nowak", 2008, Gender.FEMALE, false),
				athleteService.changeAthleteStatus(42L, new ChangeAthleteStatusRequest(false)));
	}

	@Test
	void throwsNotFoundWhenChangingStatusOfUnknownAthlete() {
		when(athleteRepository.findById(99L)).thenReturn(Optional.empty());

		assertThrows(AthleteNotFoundException.class, () -> athleteService.changeAthleteStatus(
				99L, new ChangeAthleteStatusRequest(false)));
		verify(athleteRepository, never()).saveAndFlush(any(Athlete.class));
	}

	@Test
	void translatesConcurrentUpdateUniqueConstraintViolationToDuplicateConflict() {
		Athlete athlete = new Athlete("Old", "Name", 2000, Gender.MALE);
		ReflectionTestUtils.setField(athlete, "id", 12L);
		when(athleteRepository.findById(12L)).thenReturn(Optional.of(athlete));
		when(athleteRepository.existsByFirstNameAndLastNameAndBirthYearAndIdNot(
				"New", "Name", 2010, 12L)).thenReturn(false);
		ConstraintViolationException constraintViolation = new ConstraintViolationException(
				"duplicate athlete", new SQLException("duplicate"), "uq_athletes_name_birth_year");
		when(athleteRepository.saveAndFlush(athlete))
				.thenThrow(new DataIntegrityViolationException("unique constraint", constraintViolation));

		assertThrows(AthleteAlreadyExistsException.class, () -> athleteService.updateAthlete(
				12L, new UpdateAthleteRequest("New", "Name", 2010, Gender.MALE)));
	}

	@Test
	void createsAndPersistsAnActiveAthlete() {
		CreateAthleteRequest request = new CreateAthleteRequest("John", "Smith", 2010, Gender.MALE);
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
				new CreateAthleteRequest("John", "Smith", 2010, Gender.MALE)));

		verify(athleteRepository, never()).saveAndFlush(org.mockito.ArgumentMatchers.any(Athlete.class));
	}

	@Test
	void translatesConcurrentUniqueConstraintViolationToDuplicateConflict() {
		ConstraintViolationException constraintViolation = new ConstraintViolationException(
				"duplicate athlete", new SQLException("duplicate"), "uq_athletes_name_birth_year");
		when(athleteRepository.saveAndFlush(org.mockito.ArgumentMatchers.any(Athlete.class)))
				.thenThrow(new DataIntegrityViolationException("unique constraint", constraintViolation));

		assertThrows(AthleteAlreadyExistsException.class, () -> athleteService.createAthlete(
				new CreateAthleteRequest("John", "Smith", 2010, Gender.MALE)));
	}

	@Test
	void doesNotTranslateUnrelatedDatabaseConstraintViolations() {
		ConstraintViolationException constraintViolation = new ConstraintViolationException(
				"other constraint", new SQLException("failure"), "some_other_constraint");
		when(athleteRepository.saveAndFlush(org.mockito.ArgumentMatchers.any(Athlete.class)))
				.thenThrow(new DataIntegrityViolationException("constraint", constraintViolation));

		assertThrows(DataIntegrityViolationException.class, () -> athleteService.createAthlete(
				new CreateAthleteRequest("John", "Smith", 2010, Gender.MALE)));
	}
}

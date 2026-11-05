package pl.tfhub.api.test;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;
import pl.tfhub.api.test.dto.CreateTestRequest;
import pl.tfhub.api.test.dto.TestSearchRequest;
import pl.tfhub.api.test.dto.TestResponse;

import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TestServiceTests {

	@Mock
	private TestRepository testRepository;

	@InjectMocks
	private TestService testService;

	@Test
	void createsEachCategoryWithCorrectUnitDirectionAndActiveStatus() {
		assertCreated("Sprint", "s", TestCategory.TIME, false);
		assertCreated("Long jump", "m", TestCategory.DISTANCE, true);
		assertCreated("Running speed", "m/s", TestCategory.SPEED, true);
	}

	@Test
	void trimsNameBeforeCheckingDuplicatesAndPersisting() {
		CreateTestRequest request = new CreateTestRequest("  Long jump  ", "m", TestCategory.DISTANCE, true);
		when(testRepository.existsByNormalizedName("Long jump")).thenReturn(false);
		when(testRepository.saveAndFlush(any(AthleticTest.class))).thenAnswer(invocation -> {
			AthleticTest test = invocation.getArgument(0);
			ReflectionTestUtils.setField(test, "id", 22L);
			return test;
		});

		TestResponse response = testService.createTest(request);

		assertEquals("Long jump", response.name());
		verify(testRepository).existsByNormalizedName("Long jump");
		verify(testRepository).saveAndFlush(any(AthleticTest.class));
	}

	@Test
	void rejectsDuplicateNamesIncludingCaseInsensitiveAndInactiveMatches() {
		assertDuplicate("Sprint");
		assertDuplicate("sprint");
		assertDuplicate("SPRINT");
	}

	@Test
	void translatesConcurrentNormalizedNameConstraintViolationToConflict() {
		ConstraintViolationException constraintViolation = new ConstraintViolationException(
				"duplicate test name", new SQLException("duplicate"), "uq_tests_normalized_name");
		when(testRepository.existsByNormalizedName("Sprint")).thenReturn(false);
		when(testRepository.saveAndFlush(any(AthleticTest.class)))
				.thenThrow(new DataIntegrityViolationException("unique constraint", constraintViolation));

		assertThrows(TestAlreadyExistsException.class, () -> testService.createTest(
				new CreateTestRequest("Sprint", "s", TestCategory.TIME, false)));
	}

	@Test
	void doesNotTranslateUnrelatedDatabaseConstraintViolations() {
		ConstraintViolationException constraintViolation = new ConstraintViolationException(
				"other constraint", new SQLException("failure"), "some_other_constraint");
		when(testRepository.existsByNormalizedName("Sprint")).thenReturn(false);
		when(testRepository.saveAndFlush(any(AthleticTest.class)))
				.thenThrow(new DataIntegrityViolationException("constraint", constraintViolation));

		assertThrows(DataIntegrityViolationException.class, () -> testService.createTest(
				new CreateTestRequest("Sprint", "s", TestCategory.TIME, false)));
	}

	@Test
	void retrievesActiveTestAndDoesNotModifyIt() {
		AthleticTest test = new AthleticTest("Sprint", "s", TestCategory.TIME, false);
		ReflectionTestUtils.setField(test, "id", 12L);
		when(testRepository.findById(12L)).thenReturn(java.util.Optional.of(test));

		TestResponse response = testService.getTest(12L);

		assertEquals(new TestResponse(12L, "Sprint", "s", TestCategory.TIME, false, true), response);
		verify(testRepository).findById(12L);
		verifyNoMoreInteractions(testRepository);
		assertEquals("Sprint", test.getName());
		assertEquals("s", test.getUnit());
		assertEquals(TestCategory.TIME, test.getCategory());
		assertEquals(false, test.isHigherIsBetter());
		assertEquals(true, test.isActive());
	}

	@Test
	void retrievesInactiveTest() {
		AthleticTest test = new AthleticTest("Long jump", "m", TestCategory.DISTANCE, true);
		ReflectionTestUtils.setField(test, "id", 13L);
		ReflectionTestUtils.setField(test, "active", false);
		when(testRepository.findById(13L)).thenReturn(java.util.Optional.of(test));

		assertEquals(new TestResponse(13L, "Long jump", "m", TestCategory.DISTANCE, true, false),
				testService.getTest(13L));
	}

	@Test
	void throwsNotFoundWhenTestDoesNotExist() {
		when(testRepository.findById(404L)).thenReturn(java.util.Optional.empty());

		assertThrows(TestNotFoundException.class, () -> testService.getTest(404L));
	}

	@Test
	void searchesWithEveryCriterionCombinedUsingAndAndMapsResponses() {
		AthleticTest test = new AthleticTest("Sprint 40m", "s", TestCategory.TIME, false);
		ReflectionTestUtils.setField(test, "id", 42L);
		TestSearchRequest request = new TestSearchRequest("  SPRint ", "s", TestCategory.TIME, true, false);
		when(testRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class)))
				.thenReturn(List.of(test));

		List<TestResponse> responses = testService.searchTests(request);

		assertEquals(List.of(new TestResponse(42L, "Sprint 40m", "s", TestCategory.TIME, false, true)), responses);
		ArgumentCaptor<org.springframework.data.jpa.domain.Specification<AthleticTest>> captor =
				ArgumentCaptor.forClass(org.springframework.data.jpa.domain.Specification.class);
		verify(testRepository).findAll(captor.capture());

		Root<AthleticTest> root = org.mockito.Mockito.mock(Root.class);
		CriteriaQuery<?> query = org.mockito.Mockito.mock(CriteriaQuery.class);
		CriteriaBuilder criteriaBuilder = org.mockito.Mockito.mock(CriteriaBuilder.class);
		Path<String> name = org.mockito.Mockito.mock(Path.class);
		Path<Object> unit = org.mockito.Mockito.mock(Path.class);
		Path<Object> category = org.mockito.Mockito.mock(Path.class);
		Path<Object> higherIsBetter = org.mockito.Mockito.mock(Path.class);
		Path<Object> active = org.mockito.Mockito.mock(Path.class);
		Predicate namePredicate = org.mockito.Mockito.mock(Predicate.class);
		Predicate unitPredicate = org.mockito.Mockito.mock(Predicate.class);
		Predicate categoryPredicate = org.mockito.Mockito.mock(Predicate.class);
		Predicate higherPredicate = org.mockito.Mockito.mock(Predicate.class);
		Predicate activePredicate = org.mockito.Mockito.mock(Predicate.class);
		Predicate combinedPredicate = org.mockito.Mockito.mock(Predicate.class);
		when(root.<String>get("name")).thenReturn(name);
		when(root.get("unit")).thenReturn(unit);
		when(root.get("category")).thenReturn(category);
		when(root.get("higherIsBetter")).thenReturn(higherIsBetter);
		when(root.get("active")).thenReturn(active);
		Expression<String> lowerName = org.mockito.Mockito.mock(Expression.class);
		when(criteriaBuilder.lower(name)).thenReturn(lowerName);
		when(criteriaBuilder.like(lowerName, "%sprint%")).thenReturn(namePredicate);
		when(criteriaBuilder.equal(unit, "s")).thenReturn(unitPredicate);
		when(criteriaBuilder.equal(category, TestCategory.TIME)).thenReturn(categoryPredicate);
		when(criteriaBuilder.equal(higherIsBetter, true)).thenReturn(higherPredicate);
		when(criteriaBuilder.equal(active, false)).thenReturn(activePredicate);
		when(criteriaBuilder.and(namePredicate, unitPredicate, categoryPredicate, higherPredicate, activePredicate))
				.thenReturn(combinedPredicate);

		assertEquals(combinedPredicate, captor.getValue().toPredicate(root, query, criteriaBuilder));
		verify(criteriaBuilder).and(namePredicate, unitPredicate, categoryPredicate, higherPredicate, activePredicate);
	}

	@Test
	void ignoresNullAndWhitespaceOnlyNameCriteria() {
		assertEquals(new TestSearchRequest(null, null, null, null, null),
				new TestSearchRequest(" \t ", null, null, null, null));
		when(testRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class))).thenReturn(List.of());
		testService.searchTests(new TestSearchRequest(" \t ", null, null, null, null));

		ArgumentCaptor<org.springframework.data.jpa.domain.Specification<AthleticTest>> captor =
				ArgumentCaptor.forClass(org.springframework.data.jpa.domain.Specification.class);
		verify(testRepository).findAll(captor.capture());

		Root<AthleticTest> root = org.mockito.Mockito.mock(Root.class);
		CriteriaQuery<?> query = org.mockito.Mockito.mock(CriteriaQuery.class);
		CriteriaBuilder criteriaBuilder = org.mockito.Mockito.mock(CriteriaBuilder.class);
		Predicate all = org.mockito.Mockito.mock(Predicate.class);
		when(criteriaBuilder.and()).thenReturn(all);

		assertEquals(all, captor.getValue().toPredicate(root, query, criteriaBuilder));
		verifyNoMoreInteractions(root);
	}

	private void assertCreated(String name, String unit, TestCategory category, boolean higherIsBetter) {
		when(testRepository.existsByNormalizedName(name)).thenReturn(false);
		when(testRepository.saveAndFlush(any(AthleticTest.class))).thenAnswer(invocation -> {
			AthleticTest test = invocation.getArgument(0);
			ReflectionTestUtils.setField(test, "id", 1L);
			return test;
		});

		TestResponse response = testService.createTest(
				new CreateTestRequest(name, unit, category, higherIsBetter));

		assertEquals(new TestResponse(1L, name, unit, category, higherIsBetter, true), response);
	}

	private void assertDuplicate(String name) {
		when(testRepository.existsByNormalizedName(name)).thenReturn(true);

		assertThrows(TestAlreadyExistsException.class, () -> testService.createTest(
				new CreateTestRequest(name, "s", TestCategory.TIME, false)));
		verify(testRepository, never()).saveAndFlush(any(AthleticTest.class));
	}
}

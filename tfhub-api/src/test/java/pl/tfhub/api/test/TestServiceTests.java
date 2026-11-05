package pl.tfhub.api.test;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;
import pl.tfhub.api.test.dto.CreateTestRequest;
import pl.tfhub.api.test.dto.TestResponse;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verify;
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

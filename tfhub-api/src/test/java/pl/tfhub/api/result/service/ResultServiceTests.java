package pl.tfhub.api.result.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;
import pl.tfhub.api.athlete.dto.response.AthleteResponse;
import pl.tfhub.api.athlete.enums.Gender;
import pl.tfhub.api.athlete.exception.AthleteNotFoundException;
import pl.tfhub.api.athlete.service.AthleteService;
import pl.tfhub.api.common.authorization.AccessScopeService;
import pl.tfhub.api.result.dto.request.CreateMultipleResultsRequest;
import pl.tfhub.api.result.dto.request.CreateResultEntryRequest;
import pl.tfhub.api.result.dto.request.CreateResultRequest;
import pl.tfhub.api.result.dto.request.UpdateResultRequest;
import pl.tfhub.api.result.dto.response.CreateMultipleResultsResponse;
import pl.tfhub.api.result.dto.response.ResultResponse;
import pl.tfhub.api.result.entity.TestResult;
import pl.tfhub.api.result.enums.ResultType;
import pl.tfhub.api.result.exception.InactiveAthleteException;
import pl.tfhub.api.result.exception.InactiveTestException;
import pl.tfhub.api.result.exception.InvalidResultSearchException;
import pl.tfhub.api.result.exception.MultipleResultsDuplicateAthleteException;
import pl.tfhub.api.result.exception.ResultNotFoundException;
import pl.tfhub.api.result.repository.ResultRepository;
import pl.tfhub.api.result.repository.AthleteProfileRepository;
import pl.tfhub.api.test.dto.response.TestResponse;
import pl.tfhub.api.test.enums.TestCategory;
import pl.tfhub.api.test.exception.TestNotFoundException;
import pl.tfhub.api.test.service.TestService;
import pl.tfhub.api.user.enums.UserRole;
import pl.tfhub.api.user.service.AuthenticatedUser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResultServiceTests {

	@Mock
	private AthleteService athleteService;

	@Mock
	private TestService testService;

	@Mock
	private ResultRepository resultRepository;

	@Mock
	private AthleteProfileRepository athleteProfileRepository;

	@Mock
	private AccessScopeService accessScopeService;

	@InjectMocks
	private ResultService resultService;

	@BeforeEach
	void provideAdminIdentity() {
		org.mockito.Mockito.lenient().when(accessScopeService.currentUser())
				.thenReturn(new AuthenticatedUser(1, "admin", "", UserRole.ADMIN, true));
	}

	@Test
	void retrievesResultAndReturnsOnlyResultFieldsRegardlessOfRelatedStatus() {
		TestResult result = new TestResult(
				7L, 2L, LocalDate.parse("2026-11-05"), new BigDecimal("5.420"), ResultType.TRAINING);
		ReflectionTestUtils.setField(result, "id", 32L);
		when(resultRepository.findById(32L)).thenReturn(java.util.Optional.of(result));

		ResultResponse response = resultService.getResult(32L);

		assertEquals(new ResultResponse(
				32L, 7L, 2L, LocalDate.parse("2026-11-05"), new BigDecimal("5.420"), ResultType.TRAINING),
				response);
		verify(resultRepository).findById(32L);
		verifyNoMoreInteractions(resultRepository, athleteService, testService);
	}

	@Test
	void throwsNotFoundWhenResultDoesNotExist() {
		when(resultRepository.findById(404L)).thenReturn(java.util.Optional.empty());

		assertThrows(ResultNotFoundException.class, () -> resultService.getResult(404L));
		verifyNoMoreInteractions(resultRepository, athleteService, testService);
	}

	@Test
	void deletesOnlyTheRequestedResultAndLeavesRelatedModulesUntouched() {
		TestResult selected = existingResult();
		TestResult otherResult = new TestResult(
				7L, 2L, LocalDate.parse("2026-11-06"), new BigDecimal("5.500"), ResultType.TRAINING);
		ReflectionTestUtils.setField(otherResult, "id", 33L);
		when(resultRepository.findById(32L)).thenReturn(java.util.Optional.of(selected));
		doNothing().when(resultRepository).delete(selected);

		resultService.deleteResult(32L);

		verify(resultRepository).delete(selected);
		verify(resultRepository, never()).delete(otherResult);
		verifyNoMoreInteractions(resultRepository, athleteService, testService);
	}

	@Test
	void deletedResultCanNoLongerBeRetrieved() {
		TestResult selected = existingResult();
		when(resultRepository.findById(32L))
				.thenReturn(java.util.Optional.of(selected))
				.thenReturn(java.util.Optional.empty());

		resultService.deleteResult(32L);

		assertThrows(ResultNotFoundException.class, () -> resultService.getResult(32L));
		verify(resultRepository).delete(selected);
	}

	@Test
	void throwsNotFoundAndDoesNotDeleteWhenResultDoesNotExist() {
		when(resultRepository.findById(404L)).thenReturn(java.util.Optional.empty());

		assertThrows(ResultNotFoundException.class, () -> resultService.deleteResult(404L));

		verify(resultRepository, never()).delete(any(TestResult.class));
		verifyNoMoreInteractions(resultRepository, athleteService, testService);
	}

	@Test
	void updatesAllResultFieldsInPlaceAndPreservesId() {
		TestResult result = existingResult();
		UpdateResultRequest request = updateRequest();
		when(resultRepository.findById(32L)).thenReturn(java.util.Optional.of(result));
		when(athleteService.getAthlete(8L))
				.thenReturn(new AthleteResponse(8L, "C", "D", 2001, Gender.FEMALE, true));
		when(testService.getTest(3L))
				.thenReturn(new TestResponse(3L, "Jump", "m", TestCategory.DISTANCE, true, true));
		when(resultRepository.saveAndFlush(result)).thenReturn(result);

		ResultResponse response = resultService.updateResult(32L, request);

		assertEquals(new ResultResponse(
				32L, 8L, 3L, LocalDate.parse("2026-11-06"), new BigDecimal("6.125"), ResultType.COMPETITION),
				response);
		assertEquals(32L, result.getId());
		verify(resultRepository).saveAndFlush(result);
	}

	@Test
	void updatesOnlyValueWhenOtherFieldsAreUnchanged() {
		TestResult result = existingResult();
		UpdateResultRequest request = new UpdateResultRequest(
				7L, 2L, LocalDate.parse("2026-11-05"), new BigDecimal("5.900"), ResultType.TRAINING);
		when(resultRepository.findById(32L)).thenReturn(java.util.Optional.of(result));
		when(athleteService.getAthlete(7L))
				.thenReturn(new AthleteResponse(7L, "A", "B", 2000, Gender.MALE, true));
		when(testService.getTestForResultUpdate(2L))
				.thenReturn(new TestResponse(2L, "Sprint", "s", TestCategory.TIME, false, true));
		when(resultRepository.saveAndFlush(result)).thenReturn(result);

		ResultResponse response = resultService.updateResult(32L, request);

		assertEquals(32L, response.id());
		assertEquals(7L, response.athleteId());
		assertEquals(2L, response.testId());
		assertEquals(LocalDate.parse("2026-11-05"), response.resultDate());
		assertEquals(new BigDecimal("5.900"), response.value());
		assertEquals(ResultType.TRAINING, response.type());
	}

	@Test
	void allowsUpdatingResultForInactiveAthlete() {
		TestResult result = existingResult();
		UpdateResultRequest request = updateRequest();
		when(resultRepository.findById(32L)).thenReturn(java.util.Optional.of(result));
		when(athleteService.getAthlete(8L))
				.thenReturn(new AthleteResponse(8L, "C", "D", 2001, Gender.FEMALE, false));
		when(testService.getTest(3L))
				.thenReturn(new TestResponse(3L, "Jump", "m", TestCategory.DISTANCE, true, true));
		when(resultRepository.saveAndFlush(result)).thenReturn(result);

		assertEquals(8L, resultService.updateResult(32L, request).athleteId());
		assertEquals(32L, result.getId());
	}

	@Test
	void doesNotMutateExistingResultWhenAthleteDoesNotExist() {
		TestResult result = existingResult();
		when(resultRepository.findById(32L)).thenReturn(java.util.Optional.of(result));
		when(athleteService.getAthlete(8L)).thenThrow(new AthleteNotFoundException(8));

		assertThrows(AthleteNotFoundException.class, () -> resultService.updateResult(32L, updateRequest()));
		assertExistingFieldsUnchanged(result);
		verify(resultRepository, never()).saveAndFlush(any(TestResult.class));
	}

	@Test
	void doesNotMutateExistingResultWhenTestDoesNotExist() {
		TestResult result = existingResult();
		when(resultRepository.findById(32L)).thenReturn(java.util.Optional.of(result));
		when(athleteService.getAthlete(8L))
				.thenReturn(new AthleteResponse(8L, "C", "D", 2001, Gender.FEMALE, true));
		when(testService.getTest(3L)).thenThrow(new TestNotFoundException(3));

		assertThrows(TestNotFoundException.class, () -> resultService.updateResult(32L, updateRequest()));
		assertExistingFieldsUnchanged(result);
		verify(resultRepository, never()).saveAndFlush(any(TestResult.class));
	}

	@Test
	void doesNotMutateExistingResultWhenTestIsInactive() {
		TestResult result = existingResult();
		when(resultRepository.findById(32L)).thenReturn(java.util.Optional.of(result));
		when(athleteService.getAthlete(8L))
				.thenReturn(new AthleteResponse(8L, "C", "D", 2001, Gender.FEMALE, true));
		when(testService.getTest(3L))
				.thenReturn(new TestResponse(3L, "Jump", "m", TestCategory.DISTANCE, true, false));

		assertThrows(InactiveTestException.class, () -> resultService.updateResult(32L, updateRequest()));
		assertExistingFieldsUnchanged(result);
		verify(resultRepository, never()).saveAndFlush(any(TestResult.class));
	}

	@Test
	void doesNotValidateReferencesWhenResultDoesNotExist() {
		when(resultRepository.findById(404L)).thenReturn(java.util.Optional.empty());

		assertThrows(ResultNotFoundException.class, () -> resultService.updateResult(404L, updateRequest()));
		verifyNoMoreInteractions(athleteService, testService);
		verify(resultRepository, never()).saveAndFlush(any(TestResult.class));
	}

	@Test
	void searchesResultsUsingDatabaseSpecificationAndDeterministicSort() {
		TestResult newest = new TestResult(
				7L, 2L, LocalDate.parse("2026-11-06"), new BigDecimal("5.500"), ResultType.TRAINING);
		TestResult sameDateHigherId = new TestResult(
				7L, 2L, LocalDate.parse("2026-11-05"), new BigDecimal("5.420"), ResultType.TRAINING);
		TestResult sameDateLowerId = new TestResult(
				7L, 2L, LocalDate.parse("2026-11-05"), new BigDecimal("5.300"), ResultType.TRAINING);
		ReflectionTestUtils.setField(newest, "id", 33L);
		ReflectionTestUtils.setField(sameDateHigherId, "id", 32L);
		ReflectionTestUtils.setField(sameDateLowerId, "id", 31L);
		when(resultRepository.findAll(
				org.mockito.ArgumentMatchers.<Specification<TestResult>>any(),
				org.mockito.ArgumentMatchers.any(Sort.class)))
				.thenReturn(List.of(newest, sameDateHigherId, sameDateLowerId));

		List<ResultResponse> results = resultService.searchResults(
				7L, 2L, LocalDate.parse("2026-11-01"), LocalDate.parse("2026-11-30"), ResultType.TRAINING);

		assertEquals(List.of(33L, 32L, 31L), results.stream().map(ResultResponse::id).toList());
		verify(resultRepository).findAll(
				org.mockito.ArgumentMatchers.<Specification<TestResult>>any(),
				org.mockito.ArgumentMatchers.eq(Sort.by(
						Sort.Order.desc("resultDate"),
						Sort.Order.desc("id"))));
		verifyNoMoreInteractions(resultRepository, athleteService, testService);
	}

	@Test
	void rejectsSearchWhenStartDateIsAfterEndDate() {
		assertThrows(InvalidResultSearchException.class, () -> resultService.searchResults(
				null, null, LocalDate.parse("2026-11-06"), LocalDate.parse("2026-11-05"), null));
		verifyNoMoreInteractions(resultRepository, athleteService, testService);
	}

	@Test
	void rejectsInactiveAthleteWhenCreatingResult() {
		CreateResultRequest request = request();
		when(athleteService.getAthlete(1L))
				.thenReturn(new AthleteResponse(1L, "A", "B", 2000, Gender.MALE, false));
		assertThrows(InactiveAthleteException.class, () -> resultService.createResult(request));
		verify(athleteService).getAthlete(1L);
		verify(testService, never()).getTest(anyLong());
		verify(resultRepository, never()).saveAndFlush(any(TestResult.class));
		verifyNoMoreInteractions(athleteService, testService, resultRepository);
	}

	@Test
	void createsResultForAnActiveAthleteAndActiveTest() {
		CreateResultRequest request = request();
		when(athleteService.getAthlete(1L))
				.thenReturn(new AthleteResponse(1L, "A", "B", 2000, Gender.MALE, true));
		when(testService.getTest(2L))
				.thenReturn(new TestResponse(2L, "Sprint", "s", TestCategory.TIME, false, true));
		when(resultRepository.saveAndFlush(any(TestResult.class))).thenAnswer(invocation -> {
			TestResult result = invocation.getArgument(0);
			ReflectionTestUtils.setField(result, "id", 31L);
			return result;
		});

		ResultResponse response = resultService.createResult(request);

		assertEquals(new ResultResponse(
				31L, 1L, 2L, LocalDate.parse("2026-11-05"), new BigDecimal("5.420"), ResultType.TRAINING),
				response);
		verify(athleteService).getAthlete(1L);
		verify(testService).getTest(2L);
		verify(resultRepository).saveAndFlush(any(TestResult.class));
		verifyNoMoreInteractions(athleteService, testService, resultRepository);
	}

	@Test
	void preservesDuplicateResultsForSameAthleteTestAndDate() {
		CreateResultRequest request = request();
		when(athleteService.getAthlete(1L))
				.thenReturn(new AthleteResponse(1L, "A", "B", 2000, Gender.FEMALE, true));
		when(testService.getTest(2L))
				.thenReturn(new TestResponse(2L, "Sprint", "s", TestCategory.TIME, false, true));
		when(resultRepository.saveAndFlush(any(TestResult.class))).thenAnswer(invocation -> {
			TestResult result = invocation.getArgument(0);
			ReflectionTestUtils.setField(result, "id", 31L + invocation.getArguments().length);
			return result;
		});

		resultService.createResult(request);
		resultService.createResult(request);

		verify(resultRepository, org.mockito.Mockito.times(2)).saveAndFlush(any(TestResult.class));
	}

	@Test
	void rejectsMissingAthleteWithoutSaving() {
		when(athleteService.getAthlete(1L)).thenThrow(new AthleteNotFoundException(1));

		assertThrows(AthleteNotFoundException.class, () -> resultService.createResult(request()));
		verify(testService, never()).getTest(anyLong());
		verify(resultRepository, never()).saveAndFlush(any(TestResult.class));
	}

	@Test
	void rejectsMissingTestWithoutSaving() {
		when(athleteService.getAthlete(1L))
				.thenReturn(new AthleteResponse(1L, "A", "B", 2000, Gender.MALE, true));
		when(testService.getTest(2L)).thenThrow(new TestNotFoundException(2));

		assertThrows(TestNotFoundException.class, () -> resultService.createResult(request()));
		verify(resultRepository, never()).saveAndFlush(any(TestResult.class));
	}

	@Test
	void rejectsInactiveTestWithoutSaving() {
		when(athleteService.getAthlete(1L))
				.thenReturn(new AthleteResponse(1L, "A", "B", 2000, Gender.MALE, true));
		when(testService.getTest(2L))
				.thenReturn(new TestResponse(2L, "Sprint", "s", TestCategory.TIME, false, false));

		assertThrows(InactiveTestException.class, () -> resultService.createResult(request()));
		verify(resultRepository, never()).saveAndFlush(any(TestResult.class));
	}

	@Test
	void createsBatchForActiveAthletesAndReturnsResultsInRequestOrder() {
		CreateMultipleResultsRequest request = batchRequest();
		when(testService.getTest(2L))
				.thenReturn(new TestResponse(2L, "Sprint", "s", TestCategory.TIME, false, true));
		when(athleteService.getAthlete(7L))
				.thenReturn(new AthleteResponse(7L, "A", "B", 2000, Gender.MALE, true));
		when(athleteService.getAthlete(3L))
				.thenReturn(new AthleteResponse(3L, "C", "D", 2001, Gender.FEMALE, true));
		when(resultRepository.saveAllAndFlush(any(Iterable.class))).thenAnswer(invocation -> {
			Iterable<TestResult> saved = invocation.getArgument(0);
			List<TestResult> persisted = new ArrayList<>();
			long id = 50;
			for (TestResult result : saved) {
				ReflectionTestUtils.setField(result, "id", id++);
				persisted.add(result);
			}
			return persisted;
		});

		CreateMultipleResultsResponse response = resultService.createMultipleResults(request);

		assertEquals(2, response.createdCount());
		assertEquals(List.of(7L, 3L), response.results().stream().map(ResultResponse::athleteId).toList());
		assertEquals(List.of(2L, 2L), response.results().stream().map(ResultResponse::testId).toList());
		assertEquals(List.of(new BigDecimal("5.420"), new BigDecimal("5.300")),
				response.results().stream().map(ResultResponse::value).toList());
		verify(athleteService).getAthlete(7L);
		verify(athleteService).getAthlete(3L);
		verifyNoMoreInteractions(athleteService, testService);
	}

	@Test
	void rejectsMissingTestWithoutSavingAnyResults() {
		when(testService.getTest(2L)).thenThrow(new TestNotFoundException(2));
		assertThrows(TestNotFoundException.class, () -> resultService.createMultipleResults(batchRequest()));
		verify(resultRepository, never()).saveAllAndFlush(any(Iterable.class));
	}

	@Test
	void rejectsInactiveTestWithoutSavingAnyResults() {
		when(testService.getTest(2L))
				.thenReturn(new TestResponse(2L, "Sprint", "s", TestCategory.TIME, false, false));
		assertThrows(InactiveTestException.class, () -> resultService.createMultipleResults(batchRequest()));
		verify(resultRepository, never()).saveAllAndFlush(any(Iterable.class));
	}

	@Test
	void rejectsMissingAthleteWithoutSavingAnyResults() {
		when(testService.getTest(2L))
				.thenReturn(new TestResponse(2L, "Sprint", "s", TestCategory.TIME, false, true));
		when(athleteService.getAthlete(7L))
				.thenReturn(new AthleteResponse(7L, "A", "B", 2000, Gender.MALE, true));
		when(athleteService.getAthlete(3L)).thenThrow(new AthleteNotFoundException(3));

		assertThrows(AthleteNotFoundException.class, () -> resultService.createMultipleResults(batchRequest()));
		verify(resultRepository, never()).saveAllAndFlush(any(Iterable.class));
	}

	@Test
	void rejectsInactiveAthleteWithoutSavingAnyBatchResults() {
		when(testService.getTest(2L))
				.thenReturn(new TestResponse(2L, "Sprint", "s", TestCategory.TIME, false, true));
		when(athleteService.getAthlete(7L))
				.thenReturn(new AthleteResponse(7L, "A", "B", 2000, Gender.MALE, true));
		when(athleteService.getAthlete(3L))
				.thenReturn(new AthleteResponse(3L, "C", "D", 2001, Gender.FEMALE, false));

		assertThrows(InactiveAthleteException.class, () -> resultService.createMultipleResults(batchRequest()));

		verify(resultRepository, never()).saveAllAndFlush(any(Iterable.class));
	}

	@Test
	void rejectsDuplicateAthleteIdsBeforeLookingUpAthletesOrSaving() {
		CreateMultipleResultsRequest request = new CreateMultipleResultsRequest(
				2L, LocalDate.parse("2026-11-05"), ResultType.TRAINING,
				List.of(
						new CreateResultEntryRequest(7L, new BigDecimal("5.420")),
						new CreateResultEntryRequest(7L, new BigDecimal("5.300"))));
		when(testService.getTest(2L))
				.thenReturn(new TestResponse(2L, "Sprint", "s", TestCategory.TIME, false, true));

		assertThrows(MultipleResultsDuplicateAthleteException.class,
				() -> resultService.createMultipleResults(request));
		verify(athleteService, never()).getAthlete(anyLong());
		verify(resultRepository, never()).saveAllAndFlush(any(Iterable.class));
	}

	private CreateResultRequest request() {
		return new CreateResultRequest(
				1L, 2L, LocalDate.parse("2026-11-05"), new BigDecimal("5.420"), ResultType.TRAINING);
	}

	private TestResult existingResult() {
		TestResult result = new TestResult(
				7L, 2L, LocalDate.parse("2026-11-05"), new BigDecimal("5.420"), ResultType.TRAINING);
		ReflectionTestUtils.setField(result, "id", 32L);
		return result;
	}

	private UpdateResultRequest updateRequest() {
		return new UpdateResultRequest(
				8L, 3L, LocalDate.parse("2026-11-06"), new BigDecimal("6.125"), ResultType.COMPETITION);
	}

	private void assertExistingFieldsUnchanged(TestResult result) {
		assertEquals(32L, result.getId());
		assertEquals(7L, result.getAthleteId());
		assertEquals(2L, result.getTestId());
		assertEquals(LocalDate.parse("2026-11-05"), result.getResultDate());
		assertEquals(new BigDecimal("5.420"), result.getValue());
		assertEquals(ResultType.TRAINING, result.getType());
	}

	private CreateMultipleResultsRequest batchRequest() {
		return new CreateMultipleResultsRequest(
				2L,
				LocalDate.parse("2026-11-05"),
				ResultType.TRAINING,
				List.of(
						new CreateResultEntryRequest(7L, new BigDecimal("5.420")),
						new CreateResultEntryRequest(3L, new BigDecimal("5.300"))));
	}
}

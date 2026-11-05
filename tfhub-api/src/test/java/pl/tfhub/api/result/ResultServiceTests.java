package pl.tfhub.api.result;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import pl.tfhub.api.athlete.AthleteNotFoundException;
import pl.tfhub.api.athlete.AthleteService;
import pl.tfhub.api.athlete.Gender;
import pl.tfhub.api.athlete.dto.AthleteResponse;
import pl.tfhub.api.result.dto.CreateResultRequest;
import pl.tfhub.api.result.dto.CreateMultipleResultsRequest;
import pl.tfhub.api.result.dto.CreateMultipleResultsResponse;
import pl.tfhub.api.result.dto.CreateResultEntryRequest;
import pl.tfhub.api.result.dto.ResultResponse;
import pl.tfhub.api.test.TestCategory;
import pl.tfhub.api.test.TestNotFoundException;
import pl.tfhub.api.test.TestService;
import pl.tfhub.api.test.dto.TestResponse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyLong;

@ExtendWith(MockitoExtension.class)
class ResultServiceTests {

	@Mock
	private AthleteService athleteService;

	@Mock
	private TestService testService;

	@Mock
	private ResultRepository resultRepository;

	@InjectMocks
	private ResultService resultService;

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
	void createsResultForAnInactiveAthleteAndActiveTest() {
		CreateResultRequest request = request();
		when(athleteService.getAthlete(1L))
				.thenReturn(new AthleteResponse(1L, "A", "B", 2000, Gender.MALE, false));
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
	void createsBatchForActiveAndInactiveAthletesAndReturnsResultsInRequestOrder() {
		CreateMultipleResultsRequest request = batchRequest();
		when(testService.getTest(2L))
				.thenReturn(new TestResponse(2L, "Sprint", "s", TestCategory.TIME, false, true));
		when(athleteService.getAthlete(7L))
				.thenReturn(new AthleteResponse(7L, "A", "B", 2000, Gender.MALE, true));
		when(athleteService.getAthlete(3L))
				.thenReturn(new AthleteResponse(3L, "C", "D", 2001, Gender.FEMALE, false));
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

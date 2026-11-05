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
import pl.tfhub.api.result.dto.ResultResponse;
import pl.tfhub.api.test.TestCategory;
import pl.tfhub.api.test.TestNotFoundException;
import pl.tfhub.api.test.TestService;
import pl.tfhub.api.test.dto.TestResponse;

import java.math.BigDecimal;
import java.time.LocalDate;

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

	private CreateResultRequest request() {
		return new CreateResultRequest(
				1L, 2L, LocalDate.parse("2026-11-05"), new BigDecimal("5.420"), ResultType.TRAINING);
	}
}

package pl.tfhub.api.result;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.tfhub.api.athlete.AthleteService;
import pl.tfhub.api.result.dto.CreateMultipleResultsRequest;
import pl.tfhub.api.result.dto.CreateResultRequest;
import pl.tfhub.api.result.dto.CreateResultEntryRequest;
import pl.tfhub.api.result.dto.CreateMultipleResultsResponse;
import pl.tfhub.api.result.dto.ResultResponse;
import pl.tfhub.api.test.TestService;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class ResultService {

	private final AthleteService athleteService;
	private final TestService testService;
	private final ResultRepository resultRepository;

	public ResultService(
			AthleteService athleteService,
			TestService testService,
			ResultRepository resultRepository) {
		this.athleteService = athleteService;
		this.testService = testService;
		this.resultRepository = resultRepository;
	}

	@Transactional
	public ResultResponse createResult(CreateResultRequest request) {
		athleteService.getAthlete(request.athleteId());
		requireActiveTest(request.testId());

		TestResult result = new TestResult(
				request.athleteId(),
				request.testId(),
				request.resultDate(),
				request.value(),
				request.type());
		return ResultResponse.from(resultRepository.saveAndFlush(result));
	}

	@Transactional
	public CreateMultipleResultsResponse createMultipleResults(CreateMultipleResultsRequest request) {
		requireActiveTest(request.testId());

		Set<Long> athleteIds = new HashSet<>();
		for (CreateResultEntryRequest entry : request.results()) {
			if (!athleteIds.add(entry.athleteId())) {
				throw new MultipleResultsDuplicateAthleteException();
			}
		}

		for (CreateResultEntryRequest entry : request.results()) {
			athleteService.getAthlete(entry.athleteId());
		}

		List<TestResult> results = request.results().stream()
				.map(entry -> new TestResult(
						entry.athleteId(),
						request.testId(),
						request.resultDate(),
						entry.value(),
						request.type()))
				.toList();

		resultRepository.saveAllAndFlush(results);
		List<ResultResponse> responseResults = results.stream()
				.map(ResultResponse::from)
				.toList();
		return new CreateMultipleResultsResponse(responseResults.size(), responseResults);
	}

	private void requireActiveTest(long testId) {
		if (!testService.getTest(testId).active()) {
			throw new InactiveTestException(testId);
		}
	}
}

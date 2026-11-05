package pl.tfhub.api.result;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.tfhub.api.athlete.AthleteService;
import pl.tfhub.api.result.dto.CreateResultRequest;
import pl.tfhub.api.result.dto.ResultResponse;
import pl.tfhub.api.test.TestService;

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
		if (!testService.getTest(request.testId()).active()) {
			throw new InactiveTestException(request.testId());
		}

		TestResult result = new TestResult(
				request.athleteId(),
				request.testId(),
				request.resultDate(),
				request.value(),
				request.type());
		return ResultResponse.from(resultRepository.saveAndFlush(result));
	}
}

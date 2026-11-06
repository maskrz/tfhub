package pl.tfhub.api.result.service;

import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.tfhub.api.athlete.service.AthleteService;
import pl.tfhub.api.result.dto.request.CreateMultipleResultsRequest;
import pl.tfhub.api.result.dto.request.CreateResultEntryRequest;
import pl.tfhub.api.result.dto.request.CreateResultRequest;
import pl.tfhub.api.result.dto.request.UpdateResultRequest;
import pl.tfhub.api.result.dto.response.CreateMultipleResultsResponse;
import pl.tfhub.api.result.dto.response.ResultResponse;
import pl.tfhub.api.result.entity.TestResult;
import pl.tfhub.api.result.enums.ResultType;
import pl.tfhub.api.result.exception.InactiveTestException;
import pl.tfhub.api.result.exception.InvalidResultSearchException;
import pl.tfhub.api.result.exception.MultipleResultsDuplicateAthleteException;
import pl.tfhub.api.result.exception.ResultNotFoundException;
import pl.tfhub.api.result.repository.ResultRepository;
import pl.tfhub.api.test.service.TestService;

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

	@Transactional(readOnly = true)
	public ResultResponse getResult(long id) {
		TestResult result = resultRepository.findById(id)
				.orElseThrow(() -> new ResultNotFoundException(id));
		return ResultResponse.from(result);
	}

	@Transactional
	public ResultResponse updateResult(long id, UpdateResultRequest request) {
		TestResult result = resultRepository.findById(id)
				.orElseThrow(() -> new ResultNotFoundException(id));
		athleteService.getAthlete(request.athleteId());
		requireActiveTest(request.testId());

		result.update(
				request.athleteId(),
				request.testId(),
				request.resultDate(),
				request.value(),
				request.type());
		return ResultResponse.from(resultRepository.saveAndFlush(result));
	}

	@Transactional(readOnly = true)
	public List<ResultResponse> searchResults(
			Long athleteId,
			Long testId,
			LocalDate fromDate,
			LocalDate toDate,
			ResultType type) {
		if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
			throw new InvalidResultSearchException();
		}

		Specification<TestResult> specification = (root, query, criteriaBuilder) -> {
			List<Predicate> predicates = new ArrayList<>();
			if (athleteId != null) {
				predicates.add(criteriaBuilder.equal(root.get("athleteId"), athleteId));
			}
			if (testId != null) {
				predicates.add(criteriaBuilder.equal(root.get("testId"), testId));
			}
			if (fromDate != null) {
				predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("resultDate"), fromDate));
			}
			if (toDate != null) {
				predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("resultDate"), toDate));
			}
			if (type != null) {
				predicates.add(criteriaBuilder.equal(root.get("type"), type));
			}
			return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
		};

		return resultRepository.findAll(specification, Sort.by(
						Sort.Order.desc("resultDate"),
						Sort.Order.desc("id")))
				.stream()
				.map(ResultResponse::from)
				.toList();
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

package pl.tfhub.api.result.service;

import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Subquery;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.tfhub.api.athlete.service.AthleteService;
import pl.tfhub.api.athlete.entity.Athlete;
import pl.tfhub.api.common.authorization.AccessScopeService;
import pl.tfhub.api.result.dto.request.CreateMultipleResultsRequest;
import pl.tfhub.api.result.dto.request.CreateResultEntryRequest;
import pl.tfhub.api.result.dto.request.CreateResultRequest;
import pl.tfhub.api.result.dto.request.UpdateResultRequest;
import pl.tfhub.api.result.dto.response.AthleteResultHistoryResponse;
import pl.tfhub.api.result.dto.response.CreateMultipleResultsResponse;
import pl.tfhub.api.result.dto.response.ResultResponse;
import pl.tfhub.api.result.entity.TestResult;
import pl.tfhub.api.result.enums.ResultType;
import pl.tfhub.api.result.exception.InactiveAthleteException;
import pl.tfhub.api.result.exception.InactiveTestException;
import pl.tfhub.api.result.exception.InvalidResultSearchException;
import pl.tfhub.api.result.exception.MultipleResultsDuplicateAthleteException;
import pl.tfhub.api.result.exception.ResultNotFoundException;
import pl.tfhub.api.result.repository.AthleteProfileRepository;
import pl.tfhub.api.result.repository.ResultRepository;
import pl.tfhub.api.test.dto.response.TestResponse;
import pl.tfhub.api.test.service.TestService;
import pl.tfhub.api.user.enums.UserRole;

@Service
public class ResultService {

	private final AthleteService athleteService;
	private final TestService testService;
	private final ResultRepository resultRepository;
	private final AthleteProfileRepository athleteProfileRepository;
	private final AccessScopeService accessScopeService;

	public ResultService(
			AthleteService athleteService,
			TestService testService,
			ResultRepository resultRepository,
			AthleteProfileRepository athleteProfileRepository,
			AccessScopeService accessScopeService) {
		this.athleteService = athleteService;
		this.testService = testService;
		this.resultRepository = resultRepository;
		this.athleteProfileRepository = athleteProfileRepository;
		this.accessScopeService = accessScopeService;
	}

	@Transactional(readOnly = true)
	public List<TestResponse> getTestsWithResultsForAthlete(long athleteId) {
		athleteService.getAthlete(athleteId);
		return athleteProfileRepository.findTestsWithResultsForAthlete(athleteId);
	}

	@Transactional(readOnly = true)
	public List<AthleteResultHistoryResponse> getAthleteResultHistory(
			long athleteId,
			List<Long> testIds,
			LocalDate fromDate,
			LocalDate toDate) {
		if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
			throw new InvalidResultSearchException();
		}
		if (testIds != null && testIds.stream().anyMatch(testId -> testId == null || testId <= 0)) {
			throw new InvalidResultSearchException();
		}

		athleteService.getAthlete(athleteId);
		return athleteProfileRepository.findResultHistory(athleteId, testIds, fromDate, toDate);
	}

	@Transactional
	public ResultResponse createResult(CreateResultRequest request) {
		accessScopeService.requireCoachOrAdmin();
		accessScopeService.requireAthleteWriteAccess(request.athleteId());
		requireActiveAthlete(request.athleteId());
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
		accessScopeService.requireResultReadAccess(id, result.getAthleteId());
		return ResultResponse.from(result);
	}

	@Transactional
	public ResultResponse updateResult(long id, UpdateResultRequest request) {
		accessScopeService.requireCoachOrAdmin();
		TestResult result = resultRepository.findById(id)
				.orElseThrow(() -> new ResultNotFoundException(id));
		accessScopeService.requireResultReadAccess(id, result.getAthleteId());
		accessScopeService.requireAthleteWriteAccess(result.getAthleteId());
		accessScopeService.requireAthleteWriteAccess(request.athleteId());
		athleteService.getAthlete(request.athleteId());
		if (request.testId() == result.getTestId()) {
			testService.getTestForResultUpdate(request.testId());
		} else {
			requireActiveTest(request.testId());
		}

		result.update(
				request.athleteId(),
				request.testId(),
				request.resultDate(),
				request.value(),
				request.type());
		return ResultResponse.from(resultRepository.saveAndFlush(result));
	}

	@Transactional
	public void deleteResult(long id) {
		accessScopeService.requireCoachOrAdmin();
		TestResult result = resultRepository.findById(id)
				.orElseThrow(() -> new ResultNotFoundException(id));
		accessScopeService.requireResultReadAccess(id, result.getAthleteId());
		accessScopeService.requireAthleteWriteAccess(result.getAthleteId());
		resultRepository.delete(result);
	}

	@Transactional(readOnly = true)
	public List<ResultResponse> searchResults(
			Long athleteId,
			Long testId,
			LocalDate fromDate,
			LocalDate toDate,
			ResultType type) {
		return searchResults(athleteId, testId, fromDate, toDate, type, null);
	}

	@Transactional(readOnly = true)
	public List<ResultResponse> searchResults(
			Long athleteId,
			Long testId,
			LocalDate fromDate,
			LocalDate toDate,
			ResultType type,
			List<Long> requestedGroupIds) {
		if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
			throw new InvalidResultSearchException();
		}
		UserRole role = accessScopeService.currentUser().role();
		if (role == UserRole.ATHLETE) {
			throw new org.springframework.security.access.AccessDeniedException(
					"Athlete accounts cannot access the global results list");
		}
		List<Long> groupIds = role == UserRole.COACH
				? accessScopeService.effectiveGroupIds(requestedGroupIds)
				: requestedGroupIds;

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
			if (groupIds != null) {
				Subquery<Long> athleteIds = query.subquery(Long.class);
				var athlete = athleteIds.from(Athlete.class);
				athleteIds.select(athlete.get("id"))
						.where(athlete.get("trainingGroup").get("id").in(groupIds));
				predicates.add(root.get("athleteId").in(athleteIds));
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
		accessScopeService.requireCoachOrAdmin();
		requireActiveTest(request.testId());

		Set<Long> athleteIds = new HashSet<>();
		for (CreateResultEntryRequest entry : request.results()) {
			if (!athleteIds.add(entry.athleteId())) {
				throw new MultipleResultsDuplicateAthleteException();
			}
		}

		for (CreateResultEntryRequest entry : request.results()) {
			accessScopeService.requireAthleteWriteAccess(entry.athleteId());
			requireActiveAthlete(entry.athleteId());
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

	private void requireActiveAthlete(long athleteId) {
		if (!athleteService.getAthlete(athleteId).active()) {
			throw new InactiveAthleteException(athleteId);
		}
	}
}

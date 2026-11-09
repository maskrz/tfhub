package pl.tfhub.api.test.service;

import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.tfhub.api.test.dto.request.ChangeTestStatusRequest;
import pl.tfhub.api.test.dto.request.CreateTestRequest;
import pl.tfhub.api.test.dto.request.TestSearchRequest;
import pl.tfhub.api.test.dto.request.UpdateTestRequest;
import pl.tfhub.api.test.dto.response.TestResponse;
import pl.tfhub.api.test.entity.AthleticTest;
import pl.tfhub.api.test.exception.TestAlreadyExistsException;
import pl.tfhub.api.test.exception.TestHasResultsException;
import pl.tfhub.api.test.exception.TestNotFoundException;
import pl.tfhub.api.test.repository.TestRepository;
import pl.tfhub.api.common.authorization.AccessScopeService;
import pl.tfhub.api.user.enums.UserRole;

@Service
public class TestService {

	private static final String UNIQUE_CONSTRAINT = "uq_tests_normalized_name";

	private final TestRepository testRepository;
	private final AccessScopeService accessScopeService;

	@Autowired
	public TestService(TestRepository testRepository, AccessScopeService accessScopeService) {
		this.testRepository = testRepository;
		this.accessScopeService = accessScopeService;
	}

	@Transactional
	public TestResponse createTest(CreateTestRequest request) {
		accessScopeService.requireAdmin();
		if (testRepository.existsByNormalizedName(request.name())) {
			throw new TestAlreadyExistsException();
		}

		AthleticTest test = new AthleticTest(
				request.name(),
				request.unit(),
				request.category(),
				request.higherIsBetter());
		try {
			return TestResponse.from(testRepository.saveAndFlush(test));
		} catch (DataIntegrityViolationException exception) {
			if (isDuplicateTestConstraint(exception)) {
				throw new TestAlreadyExistsException();
			}
			throw exception;
		}
	}

	@Transactional(readOnly = true)
	public TestResponse getTest(long id) {
		UserRole role = accessScopeService.currentUser().role();
		if (role == UserRole.ATHLETE) {
			throw new org.springframework.security.access.AccessDeniedException("Athlete accounts cannot browse tests");
		}
		AthleticTest test = testRepository.findById(id)
				.orElseThrow(() -> new TestNotFoundException(id));
		if (role == UserRole.COACH && !test.isActive()) {
			throw new TestNotFoundException(id);
		}
		return TestResponse.from(test);
	}

	@Transactional(readOnly = true)
	public TestResponse getTestForResultUpdate(long id) {
		accessScopeService.requireCoachOrAdmin();
		AthleticTest test = testRepository.findById(id)
				.orElseThrow(() -> new TestNotFoundException(id));
		return TestResponse.from(test);
	}

	@Transactional
	public TestResponse updateTest(long id, UpdateTestRequest request) {
		accessScopeService.requireAdmin();
		AthleticTest test = testRepository.findById(id)
				.orElseThrow(() -> new TestNotFoundException(id));

		if (testRepository.existsByNormalizedNameAndIdNot(request.name(), id)) {
			throw new TestAlreadyExistsException();
		}

		boolean measurementConfigurationChanged =
				!test.getUnit().equals(request.unit()) || test.getCategory() != request.category();
		if (measurementConfigurationChanged && testRepository.existsResultsByTestId(id)) {
			throw new TestHasResultsException();
		}

		test.updateDefinition(request.name(), request.unit(), request.category(), request.higherIsBetter());
		try {
			return TestResponse.from(testRepository.saveAndFlush(test));
		} catch (DataIntegrityViolationException exception) {
			if (isDuplicateTestConstraint(exception)) {
				throw new TestAlreadyExistsException();
			}
			throw exception;
		}
	}

	@Transactional
	public TestResponse changeTestStatus(long id, ChangeTestStatusRequest request) {
		accessScopeService.requireAdmin();
		AthleticTest test = testRepository.findById(id)
				.orElseThrow(() -> new TestNotFoundException(id));
		test.changeStatus(request.active());
		return TestResponse.from(testRepository.saveAndFlush(test));
	}

	@Transactional(readOnly = true)
	public List<TestResponse> searchTests(TestSearchRequest request) {
		UserRole role = accessScopeService.currentUser().role();
		if (role == UserRole.ATHLETE) {
			throw new org.springframework.security.access.AccessDeniedException("Athlete accounts cannot browse tests");
		}
		Specification<AthleticTest> specification = (root, query, criteriaBuilder) -> {
			List<Predicate> predicates = new ArrayList<>();
			if (request.name() != null) {
				predicates.add(criteriaBuilder.like(
						criteriaBuilder.lower(root.get("name")),
						"%" + request.name().toLowerCase(Locale.ROOT) + "%"));
			}
			if (request.unit() != null) {
				predicates.add(criteriaBuilder.equal(root.get("unit"), request.unit()));
			}
			if (request.category() != null) {
				predicates.add(criteriaBuilder.equal(root.get("category"), request.category()));
			}
			if (request.higherIsBetter() != null) {
				predicates.add(criteriaBuilder.equal(root.get("higherIsBetter"), request.higherIsBetter()));
			}
			if (role == UserRole.COACH) {
				predicates.add(criteriaBuilder.isTrue(root.get("active")));
			} else if (request.active() != null) {
				predicates.add(criteriaBuilder.equal(root.get("active"), request.active()));
			}
			return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
		};

		return testRepository.findAll(specification)
				.stream()
				.map(TestResponse::from)
				.toList();
	}

	private boolean isDuplicateTestConstraint(Throwable exception) {
		Throwable cause = exception;
		while (cause != null) {
			if (cause instanceof ConstraintViolationException violation
					&& UNIQUE_CONSTRAINT.equals(violation.getConstraintName())) {
				return true;
			}
			cause = cause.getCause();
		}
		return false;
	}
}

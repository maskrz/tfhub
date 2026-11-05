package pl.tfhub.api.test;

import jakarta.persistence.criteria.Predicate;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.tfhub.api.test.dto.CreateTestRequest;
import pl.tfhub.api.test.dto.TestSearchRequest;
import pl.tfhub.api.test.dto.TestResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class TestService {

	private static final String UNIQUE_CONSTRAINT = "uq_tests_normalized_name";

	private final TestRepository testRepository;

	public TestService(TestRepository testRepository) {
		this.testRepository = testRepository;
	}

	@Transactional
	public TestResponse createTest(CreateTestRequest request) {
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
		AthleticTest test = testRepository.findById(id)
				.orElseThrow(() -> new TestNotFoundException(id));
		return TestResponse.from(test);
	}

	@Transactional(readOnly = true)
	public List<TestResponse> searchTests(TestSearchRequest request) {
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
			if (request.active() != null) {
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

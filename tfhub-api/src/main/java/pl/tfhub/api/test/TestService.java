package pl.tfhub.api.test;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.tfhub.api.test.dto.CreateTestRequest;
import pl.tfhub.api.test.dto.TestResponse;

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

package pl.tfhub.api.result.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import pl.tfhub.api.result.entity.TestResult;

public interface ResultRepository extends JpaRepository<TestResult, Long>, JpaSpecificationExecutor<TestResult> {
}

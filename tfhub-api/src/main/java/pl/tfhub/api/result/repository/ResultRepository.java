package pl.tfhub.api.result.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.tfhub.api.result.entity.TestResult;

public interface ResultRepository extends JpaRepository<TestResult, Long> {
}

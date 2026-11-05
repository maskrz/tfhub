package pl.tfhub.api.test.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pl.tfhub.api.test.entity.AthleticTest;

public interface TestRepository extends JpaRepository<AthleticTest, Long>, JpaSpecificationExecutor<AthleticTest> {

	@Query(value = """
			SELECT EXISTS (
				SELECT 1
				FROM tests
				WHERE LOWER(TRIM(name)) = LOWER(TRIM(:name))
			)
			""", nativeQuery = true)
	boolean existsByNormalizedName(@Param("name") String name);

	@Query(value = """
			SELECT EXISTS (
				SELECT 1
				FROM tests
				WHERE LOWER(TRIM(name)) = LOWER(TRIM(:name))
				  AND id <> :excludedId
			)
			""", nativeQuery = true)
	boolean existsByNormalizedNameAndIdNot(
			@Param("name") String name,
			@Param("excludedId") long excludedId);

	@Query(value = "SELECT EXISTS (SELECT 1 FROM test_results WHERE test_id = :testId)", nativeQuery = true)
	boolean existsResultsByTestId(@Param("testId") long testId);
}

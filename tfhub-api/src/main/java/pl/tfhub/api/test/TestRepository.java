package pl.tfhub.api.test;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TestRepository extends JpaRepository<AthleticTest, Long> {

	@Query(value = """
			SELECT EXISTS (
				SELECT 1
				FROM tests
				WHERE LOWER(TRIM(name)) = LOWER(TRIM(:name))
			)
			""", nativeQuery = true)
	boolean existsByNormalizedName(@Param("name") String name);
}

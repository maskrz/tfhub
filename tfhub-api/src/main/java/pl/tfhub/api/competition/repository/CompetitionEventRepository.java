package pl.tfhub.api.competition.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pl.tfhub.api.competition.entity.CompetitionEvent;

public interface CompetitionEventRepository extends JpaRepository<CompetitionEvent, Long> {

	List<CompetitionEvent> findAllByOrderByNameAsc();

	Optional<CompetitionEvent> findByNameIgnoreCase(String name);

	@Query(value = """
			SELECT * FROM competition_events
			WHERE LOWER(TRIM(name)) = LOWER(TRIM(:name))
			""", nativeQuery = true)
	Optional<CompetitionEvent> findByNormalizedName(@Param("name") String name);

	@Query(value = """
			SELECT EXISTS (
				SELECT 1 FROM competition_events
				WHERE LOWER(TRIM(name)) = LOWER(TRIM(:name))
			)
			""", nativeQuery = true)
	boolean existsByNormalizedName(@Param("name") String name);

	@Query(value = """
			SELECT EXISTS (
				SELECT 1 FROM competition_events
				WHERE LOWER(TRIM(name)) = LOWER(TRIM(:name)) AND id <> :excludedId
			)
			""", nativeQuery = true)
	boolean existsByNormalizedNameAndIdNot(
			@Param("name") String name,
			@Param("excludedId") long excludedId);

	@Query(value = """
			SELECT EXISTS (
				SELECT 1 FROM competition_results WHERE event_id = :eventId
			)
			""", nativeQuery = true)
	boolean existsResultsByEventId(@Param("eventId") long eventId);
}

package pl.tfhub.api.competition.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pl.tfhub.api.competition.entity.CompetitionResult;

public interface CompetitionResultRepository extends JpaRepository<CompetitionResult, Long> {

	List<CompetitionResult> findByAthleteIdOrderByResultDateDescIdDesc(long athleteId);

	Optional<CompetitionResult> findByAthleteIdAndSourceRecordKey(
			long athleteId, String sourceRecordKey);

	Optional<CompetitionResult> findByAthleteIdAndSourceFingerprint(
			long athleteId, String sourceFingerprint);

	@Query("""
			SELECT result FROM CompetitionResult result
			JOIN FETCH result.event
			WHERE result.athleteId = :athleteId
			ORDER BY result.resultDate DESC, result.id DESC
			""")
	List<CompetitionResult> findProfileResults(@Param("athleteId") long athleteId);
}

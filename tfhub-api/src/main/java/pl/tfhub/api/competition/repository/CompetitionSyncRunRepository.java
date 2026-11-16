package pl.tfhub.api.competition.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import pl.tfhub.api.competition.entity.CompetitionSyncRun;
import pl.tfhub.api.competition.enums.CompetitionSyncStatus;

public interface CompetitionSyncRunRepository extends JpaRepository<CompetitionSyncRun, Long> {

	Optional<CompetitionSyncRun> findFirstByAthleteIdOrderByRequestedAtDescIdDesc(long athleteId);

	Optional<CompetitionSyncRun> findFirstByAthleteIdAndStatusOrderByFinishedAtDescIdDesc(
			long athleteId, CompetitionSyncStatus status);

	List<CompetitionSyncRun> findByStatusOrderByRequestedAtAsc(CompetitionSyncStatus status);
}

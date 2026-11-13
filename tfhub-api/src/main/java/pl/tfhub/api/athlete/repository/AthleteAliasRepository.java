package pl.tfhub.api.athlete.repository;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pl.tfhub.api.athlete.entity.AthleteAlias;

public interface AthleteAliasRepository extends JpaRepository<AthleteAlias, Long> {

	List<AthleteAlias> findByAthleteIdInOrderByIdAsc(Collection<Long> athleteIds);

	void deleteByAthleteId(long athleteId);

	@Query(value = "SELECT normalize_athlete_alias(:value)", nativeQuery = true)
	String normalize(@Param("value") String value);
}

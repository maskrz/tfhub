package pl.tfhub.api.athlete.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pl.tfhub.api.athlete.entity.Athlete;

public interface AthleteRepository extends JpaRepository<Athlete, Long>, JpaSpecificationExecutor<Athlete> {

	boolean existsByFirstNameAndLastNameAndBirthYear(String firstName, String lastName, Integer birthYear);

	boolean existsByFirstNameAndLastNameAndBirthYearAndIdNot(
			String firstName, String lastName, Integer birthYear, Long id);

	List<Athlete> findByTrainingGroup_IdOrderByLastNameAscFirstNameAscIdAsc(long trainingGroupId);

	@Query(value = """
			SELECT a.id AS id, a.first_name AS "firstName", a.last_name AS "lastName",
			       normalize_athlete_alias(a.first_name || ' ' || a.last_name) AS "normalizedFullName",
			       aa.normalized_alias AS "normalizedAlias"
			FROM athletes a
			LEFT JOIN athlete_aliases aa ON aa.athlete_id = a.id
			WHERE a.training_group_id = :groupId AND a.active
			ORDER BY a.id, aa.id
			""", nativeQuery = true)
	List<ImportCandidateRow> findImportCandidateRows(@Param("groupId") long groupId);

	interface ImportCandidateRow {
		Long getId();
		String getFirstName();
		String getLastName();
		String getNormalizedFullName();
		String getNormalizedAlias();
	}

	@Query("""
			SELECT athlete
			FROM Athlete athlete
			WHERE athlete.trainingGroup.id = :trainingGroupId
			ORDER BY LOWER(athlete.lastName), LOWER(athlete.firstName), athlete.id
			""")
	List<Athlete> findAllCandidatesInTrainingGroup(@Param("trainingGroupId") long trainingGroupId);

	@Query("""
			SELECT athlete
			FROM Athlete athlete
			WHERE athlete.trainingGroup.id = :trainingGroupId
				AND (LOWER(athlete.firstName) LIKE CONCAT('%', :query, '%')
					OR LOWER(athlete.lastName) LIKE CONCAT('%', :query, '%')
					OR LOWER(CONCAT(athlete.firstName, ' ', athlete.lastName))
						LIKE CONCAT('%', :query, '%'))
			ORDER BY LOWER(athlete.lastName), LOWER(athlete.firstName), athlete.id
			""")
	List<Athlete> findCandidatesInTrainingGroup(
			@Param("trainingGroupId") long trainingGroupId,
			@Param("query") String query);

	boolean existsByIdAndTrainingGroup_Id(long id, long trainingGroupId);

	Optional<Athlete> findByIdAndTrainingGroup_Id(long id, long trainingGroupId);
}

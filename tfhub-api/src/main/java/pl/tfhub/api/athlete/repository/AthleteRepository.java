package pl.tfhub.api.athlete.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import pl.tfhub.api.athlete.entity.Athlete;

public interface AthleteRepository extends JpaRepository<Athlete, Long>, JpaSpecificationExecutor<Athlete> {

	boolean existsByFirstNameAndLastNameAndBirthYear(String firstName, String lastName, Integer birthYear);

	boolean existsByFirstNameAndLastNameAndBirthYearAndIdNot(
			String firstName, String lastName, Integer birthYear, Long id);
}

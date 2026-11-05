package pl.tfhub.api.athlete;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AthleteRepository extends JpaRepository<Athlete, Long>, JpaSpecificationExecutor<Athlete> {

	boolean existsByFirstNameAndLastNameAndBirthYear(String firstName, String lastName, Integer birthYear);
}

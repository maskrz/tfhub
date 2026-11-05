package pl.tfhub.api.athlete;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AthleteRepository extends JpaRepository<Athlete, Long> {

	boolean existsByFirstNameAndLastNameAndBirthYear(String firstName, String lastName, Integer birthYear);
}

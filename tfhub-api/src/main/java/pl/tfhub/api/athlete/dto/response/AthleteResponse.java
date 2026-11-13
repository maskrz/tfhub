package pl.tfhub.api.athlete.dto.response;

import pl.tfhub.api.athlete.entity.Athlete;
import pl.tfhub.api.athlete.enums.Gender;
import java.util.List;

public record AthleteResponse(
		Long id,
		String firstName,
		String lastName,
		Integer birthYear,
		Gender gender,
		boolean active,
		Long trainingGroupId,
		List<String> aliases) {

	public AthleteResponse(
			Long id,
			String firstName,
			String lastName,
			Integer birthYear,
			Gender gender,
			boolean active) {
		this(id, firstName, lastName, birthYear, gender, active, null, List.of());
	}

	public AthleteResponse(Long id, String firstName, String lastName, Integer birthYear, Gender gender,
			boolean active, Long trainingGroupId) {
		this(id, firstName, lastName, birthYear, gender, active, trainingGroupId, List.of());
	}

	public static AthleteResponse from(Athlete athlete) {
		return from(athlete, List.of());
	}

	public static AthleteResponse from(Athlete athlete, List<String> aliases) {
		return new AthleteResponse(
				athlete.getId(),
				athlete.getFirstName(),
				athlete.getLastName(),
				athlete.getBirthYear(),
				athlete.getGender(),
				athlete.isActive(),
				athlete.getTrainingGroup() == null ? null : athlete.getTrainingGroup().getId(),
				List.copyOf(aliases));
	}
}

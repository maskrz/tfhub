package pl.tfhub.api.athlete.dto;

import pl.tfhub.api.athlete.Athlete;
import pl.tfhub.api.athlete.Gender;

public record AthleteResponse(
		Long id,
		String firstName,
		String lastName,
		Integer birthYear,
		Gender gender,
		boolean active) {

	public static AthleteResponse from(Athlete athlete) {
		return new AthleteResponse(
				athlete.getId(),
				athlete.getFirstName(),
				athlete.getLastName(),
				athlete.getBirthYear(),
				athlete.getGender(),
				athlete.isActive());
	}
}

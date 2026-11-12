package pl.tfhub.api.trainingimport.dto.response;

import pl.tfhub.api.athlete.entity.Athlete;
import pl.tfhub.api.athlete.enums.Gender;

public record TrainingImportAthleteCandidateResponse(
		long id,
		String firstName,
		String lastName,
		Integer birthYear,
		Gender gender,
		boolean active) {

	public static TrainingImportAthleteCandidateResponse from(Athlete athlete) {
		return new TrainingImportAthleteCandidateResponse(
				athlete.getId(),
				athlete.getFirstName(),
				athlete.getLastName(),
				athlete.getBirthYear(),
				athlete.getGender(),
				athlete.isActive());
	}
}

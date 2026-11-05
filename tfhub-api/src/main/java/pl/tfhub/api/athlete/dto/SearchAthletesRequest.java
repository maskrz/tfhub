package pl.tfhub.api.athlete.dto;

import jakarta.validation.constraints.Min;
import pl.tfhub.api.athlete.Gender;
import pl.tfhub.api.athlete.validation.ValidBirthYear;
import tools.jackson.databind.annotation.JsonDeserialize;

public record SearchAthletesRequest(
		String firstName,
		String lastName,
		Boolean active,
		Gender gender,
		@Min(1900) @ValidBirthYear @JsonDeserialize(using = StrictIntegerDeserializer.class) Integer birthYear) {

	public SearchAthletesRequest {
		firstName = normalize(firstName);
		lastName = normalize(lastName);
	}

	private static String normalize(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.strip();
		return trimmed.isEmpty() ? null : trimmed;
	}
}

package pl.tfhub.api.athlete.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import pl.tfhub.api.athlete.dto.request.ValidBirthYear;
import pl.tfhub.api.athlete.enums.Gender;
import tools.jackson.databind.annotation.JsonDeserialize;

public record UpdateAthleteRequest(
		@NotBlank @Size(max = 100) String firstName,
		@NotBlank @Size(max = 100) String lastName,
		@NotNull @Min(1900) @ValidBirthYear
		@JsonDeserialize(using = StrictIntegerDeserializer.class) Integer birthYear,
		@NotNull Gender gender) {

	public UpdateAthleteRequest {
		firstName = trim(firstName);
		lastName = trim(lastName);
	}

	private static String trim(String value) {
		return value == null ? null : value.strip();
	}
}

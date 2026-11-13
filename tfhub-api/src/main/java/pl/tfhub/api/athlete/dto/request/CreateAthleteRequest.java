package pl.tfhub.api.athlete.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Positive;
import java.util.List;
import pl.tfhub.api.athlete.dto.request.ValidBirthYear;
import pl.tfhub.api.athlete.enums.Gender;
import pl.tfhub.api.common.validation.StrictLongDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;

public record CreateAthleteRequest(
		@NotBlank @Size(max = 100) String firstName,
		@NotBlank @Size(max = 100) String lastName,
		@NotNull @Min(1900) @ValidBirthYear @JsonDeserialize(using = StrictIntegerDeserializer.class) Integer birthYear,
		@NotNull Gender gender,
		@Positive @JsonDeserialize(using = StrictLongDeserializer.class) Long trainingGroupId,
		List<@NotBlank @Size(max = 255) String> aliases) {

	public CreateAthleteRequest(String firstName, String lastName, Integer birthYear, Gender gender) {
		this(firstName, lastName, birthYear, gender, null, null);
	}

	public CreateAthleteRequest(String firstName, String lastName, Integer birthYear, Gender gender, Long trainingGroupId) {
		this(firstName, lastName, birthYear, gender, trainingGroupId, null);
	}

	public CreateAthleteRequest {
		firstName = trim(firstName);
		lastName = trim(lastName);
	}

	private static String trim(String value) {
		return value == null ? null : value.strip();
	}
}

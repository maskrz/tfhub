package pl.tfhub.api.test.dto.request;

import jakarta.validation.constraints.Pattern;
import pl.tfhub.api.common.validation.StrictBooleanDeserializer;
import pl.tfhub.api.test.enums.TestCategory;
import tools.jackson.databind.annotation.JsonDeserialize;

public record TestSearchRequest(
		String name,
		@Pattern(regexp = "s|m|m/s") String unit,
		TestCategory category,
		@JsonDeserialize(using = StrictBooleanDeserializer.class) Boolean higherIsBetter,
		@JsonDeserialize(using = StrictBooleanDeserializer.class) Boolean active) {

	public TestSearchRequest {
		name = normalize(name);
	}

	private static String normalize(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.strip();
		return trimmed.isEmpty() ? null : trimmed;
	}
}

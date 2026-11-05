package pl.tfhub.api.test.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import pl.tfhub.api.common.validation.StrictBooleanDeserializer;
import pl.tfhub.api.test.enums.TestCategory;
import tools.jackson.databind.annotation.JsonDeserialize;

public record UpdateTestRequest(
		@NotBlank @Size(max = 150) String name,
		@NotBlank @Pattern(regexp = "s|m|m/s") String unit,
		@NotNull TestCategory category,
		@NotNull @JsonDeserialize(using = StrictBooleanDeserializer.class) Boolean higherIsBetter) {

	public UpdateTestRequest {
		name = name == null ? null : name.strip();
	}
}

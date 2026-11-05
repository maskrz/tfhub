package pl.tfhub.api.test.dto;

import jakarta.validation.constraints.NotNull;
import pl.tfhub.api.validation.StrictBooleanDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;

public record ChangeTestStatusRequest(
		@NotNull @JsonDeserialize(using = StrictBooleanDeserializer.class) Boolean active) {
}

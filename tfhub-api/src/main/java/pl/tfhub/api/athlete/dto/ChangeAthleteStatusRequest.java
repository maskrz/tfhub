package pl.tfhub.api.athlete.dto;

import jakarta.validation.constraints.NotNull;
import tools.jackson.databind.annotation.JsonDeserialize;

public record ChangeAthleteStatusRequest(
		@NotNull @JsonDeserialize(using = StrictBooleanDeserializer.class) Boolean active) {
}

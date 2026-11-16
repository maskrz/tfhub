package pl.tfhub.api.competition.dto.request;

import jakarta.validation.constraints.NotNull;
import pl.tfhub.api.common.validation.StrictBooleanDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;

public record ChangeCompetitionEventStatusRequest(
		@NotNull @JsonDeserialize(using = StrictBooleanDeserializer.class) Boolean active) {
}

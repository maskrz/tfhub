package pl.tfhub.api.training.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import pl.tfhub.api.common.validation.StrictLongDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;

public record EditTrainingSegmentRequest(
        @Positive @JsonDeserialize(using = StrictLongDeserializer.class) Long id,
        @NotNull @Positive Integer sequenceNumber,
        @NotNull @Positive Integer distanceM,
        @PositiveOrZero Integer restAfterSeconds) {
}

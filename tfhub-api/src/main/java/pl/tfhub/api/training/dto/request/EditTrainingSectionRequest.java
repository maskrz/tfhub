package pl.tfhub.api.training.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import pl.tfhub.api.common.validation.StrictLongDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;

public record EditTrainingSectionRequest(
        @Positive @JsonDeserialize(using = StrictLongDeserializer.class) Long id,
        @NotBlank String title,
        @NotBlank String content,
        @NotNull @Positive Integer sequenceNumber) {
}

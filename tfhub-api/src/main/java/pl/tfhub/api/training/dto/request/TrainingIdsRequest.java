package pl.tfhub.api.training.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;
import pl.tfhub.api.common.validation.StrictLongDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;

public record TrainingIdsRequest(
        @NotEmpty
        @JsonDeserialize(contentUsing = StrictLongDeserializer.class)
        List<@NotNull @Positive Long> trainingIds) {
}

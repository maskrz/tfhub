package pl.tfhub.api.training.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;
import pl.tfhub.api.common.validation.StrictLongDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;

public record EditTrainingParticipationRequest(
        @Positive @JsonDeserialize(using = StrictLongDeserializer.class) Long id,
        @NotNull @Positive @JsonDeserialize(using = StrictLongDeserializer.class) Long athleteId,
        List<@NotNull @Valid EditTrainingExecutionRequest> executionUpdates) {
}

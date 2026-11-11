package pl.tfhub.api.training.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;
import pl.tfhub.api.common.validation.StrictLongDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;

public record CompleteTrainingRequest(
        @NotNull List<@NotNull @Valid AddTrainingParticipationRequest> add,
        @NotNull List<@NotNull @Valid UpdateTrainingParticipationRequest> update,
        @NotNull
        @JsonDeserialize(contentUsing = StrictLongDeserializer.class)
        List<@NotNull @Positive Long> removeParticipationIds) {
}

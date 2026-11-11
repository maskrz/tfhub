package pl.tfhub.api.training.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import pl.tfhub.api.common.validation.StrictLongDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;

public record EditTrainingDefinitionRequest(
        @Positive @JsonDeserialize(using = StrictLongDeserializer.class) Long id,
        @NotBlank @Size(max = 255) String name,
        @NotNull @Positive Integer sequenceNumber,
        @NotEmpty List<@NotNull @Valid EditTrainingSegmentRequest> segments) {

    public EditTrainingDefinitionRequest {
        name = name == null ? null : name.strip();
    }
}

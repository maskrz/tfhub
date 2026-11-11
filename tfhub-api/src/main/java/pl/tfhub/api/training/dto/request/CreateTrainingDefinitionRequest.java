package pl.tfhub.api.training.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CreateTrainingDefinitionRequest(
        @NotBlank @Size(max = 255) String name,
        @NotNull @Positive Integer sequenceNumber,
        @NotEmpty List<@NotNull @Valid CreateTrainingSegmentRequest> segments,
        List<@NotNull @Positive Long> athleteIds) {

    public CreateTrainingDefinitionRequest {
        name = name == null ? null : name.strip();
    }
}

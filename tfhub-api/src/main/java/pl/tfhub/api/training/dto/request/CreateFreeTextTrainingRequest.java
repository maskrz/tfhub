package pl.tfhub.api.training.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import pl.tfhub.api.common.validation.StrictLongDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;

public record CreateFreeTextTrainingRequest(
        @NotBlank @Size(max = 255) String name,
        @NotNull LocalDate trainingDate,
        @NotNull @Positive @JsonDeserialize(using = StrictLongDeserializer.class) Long trainingGroupId,
        @NotEmpty List<@NotNull @Valid CreateTrainingSectionRequest> sections) {

    public CreateFreeTextTrainingRequest {
        name = name == null ? null : name.strip();
    }
}

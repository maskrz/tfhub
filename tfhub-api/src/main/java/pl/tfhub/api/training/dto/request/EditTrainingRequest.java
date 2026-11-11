package pl.tfhub.api.training.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

public record EditTrainingRequest(
        @NotBlank @Size(max = 255) String name,
        @NotNull LocalDate trainingDate,
        @NotEmpty List<@NotNull @Valid EditTrainingDefinitionRequest> definitions) {

    public EditTrainingRequest {
        name = name == null ? null : name.strip();
    }
}

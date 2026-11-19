package pl.tfhub.api.training.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

public record TrainingJsonImportRequest(
        @NotNull @Positive Integer formatVersion,
        @NotEmpty @Size(max = 100) List<@NotNull @Valid TrainingJsonImportItemRequest> trainings) {
}

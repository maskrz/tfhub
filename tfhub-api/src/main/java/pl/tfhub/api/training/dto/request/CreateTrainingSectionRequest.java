package pl.tfhub.api.training.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreateTrainingSectionRequest(
        @NotBlank String title,
        @NotBlank String content,
        @NotNull @Positive Integer sequenceNumber) {
}

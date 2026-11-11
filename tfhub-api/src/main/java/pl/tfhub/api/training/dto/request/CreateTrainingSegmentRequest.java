package pl.tfhub.api.training.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

public record CreateTrainingSegmentRequest(
        @NotNull @Positive Integer sequenceNumber,
        @NotNull @Positive Integer distanceM,
        @PositiveOrZero Integer restAfterSeconds) {
}

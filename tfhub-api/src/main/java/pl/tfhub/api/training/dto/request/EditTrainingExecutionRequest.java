package pl.tfhub.api.training.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import pl.tfhub.api.common.validation.StrictLongDeserializer;
import pl.tfhub.api.training.enums.TrainingExecutionStatus;
import pl.tfhub.api.result.dto.request.StrictBigDecimalDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;

public record EditTrainingExecutionRequest(
        @NotNull @Positive @JsonDeserialize(using = StrictLongDeserializer.class) Long id,
        @NotNull TrainingExecutionStatus status,
        @DecimalMin("0.0") @Digits(integer = 9, fraction = 3)
                @JsonDeserialize(using = StrictBigDecimalDeserializer.class)
                BigDecimal measuredResult,
        @NotNull Boolean usesSpikes) {
}

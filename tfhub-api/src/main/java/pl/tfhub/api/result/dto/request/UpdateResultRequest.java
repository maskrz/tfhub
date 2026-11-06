package pl.tfhub.api.result.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;
import pl.tfhub.api.result.enums.ResultType;
import tools.jackson.databind.annotation.JsonDeserialize;

public record UpdateResultRequest(
		@NotNull @Positive @JsonDeserialize(using = StrictLongDeserializer.class) Long athleteId,
		@NotNull @Positive @JsonDeserialize(using = StrictLongDeserializer.class) Long testId,
		@NotNull LocalDate resultDate,
		@NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 9, fraction = 3)
		@JsonDeserialize(using = StrictBigDecimalDeserializer.class) BigDecimal value,
		@NotNull ResultType type) {
}

package pl.tfhub.api.result.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import pl.tfhub.api.result.enums.ResultType;
import tools.jackson.databind.annotation.JsonDeserialize;

public record CreateMultipleResultsRequest(
		@NotNull @Positive @JsonDeserialize(using = StrictLongDeserializer.class) Long testId,
		@NotNull LocalDate resultDate,
		@NotNull ResultType type,
		@NotEmpty @Size(max = 100) List<@NotNull @Valid CreateResultEntryRequest> results) {
}

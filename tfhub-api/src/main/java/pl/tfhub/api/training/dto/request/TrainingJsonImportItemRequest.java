package pl.tfhub.api.training.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import java.util.List;
import pl.tfhub.api.common.validation.StrictLongDeserializer;
import pl.tfhub.api.training.enums.TrainingAssignmentType;
import tools.jackson.databind.annotation.JsonDeserialize;

public record TrainingJsonImportItemRequest(
        @NotNull Boolean selected,
        LocalDate trainingDate,
        TrainingAssignmentType assignmentType,
        @Positive @JsonDeserialize(using = StrictLongDeserializer.class) Long trainingGroupId,
        @JsonDeserialize(contentUsing = StrictLongDeserializer.class)
        List<@NotNull @Positive Long> athleteIds) {
}

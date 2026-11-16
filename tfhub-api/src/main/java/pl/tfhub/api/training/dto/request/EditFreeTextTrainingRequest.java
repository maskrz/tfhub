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
import pl.tfhub.api.training.enums.TrainingAssignmentType;
import tools.jackson.databind.annotation.JsonDeserialize;

public record EditFreeTextTrainingRequest(
        @NotBlank @Size(max = 255) String name,
        LocalDate trainingDate,
        @NotNull TrainingAssignmentType assignmentType,
        @Positive @JsonDeserialize(using = StrictLongDeserializer.class) Long trainingGroupId,
        @JsonDeserialize(contentUsing = StrictLongDeserializer.class)
        List<@NotNull @Positive Long> athleteIds,
        @NotEmpty List<@NotNull @Valid EditTrainingSectionRequest> sections) {

    public EditFreeTextTrainingRequest {
        name = name == null ? null : name.strip();
    }
}

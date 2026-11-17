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
import tools.jackson.databind.JsonNode;

public record EditFreeTextTrainingRequest(
        @NotBlank @Size(max = 255) String name,
        LocalDate trainingDate,
        @NotNull TrainingAssignmentType assignmentType,
        @Positive @JsonDeserialize(using = StrictLongDeserializer.class) Long trainingGroupId,
        @JsonDeserialize(contentUsing = StrictLongDeserializer.class)
        List<@NotNull @Positive Long> athleteIds,
        @NotEmpty List<@NotNull @Valid EditTrainingSectionRequest> sections,
        JsonNode equipment) {

    public EditFreeTextTrainingRequest(String name, LocalDate trainingDate, TrainingAssignmentType assignmentType,
            Long trainingGroupId, List<Long> athleteIds, List<EditTrainingSectionRequest> sections) {
        this(name, trainingDate, assignmentType, trainingGroupId, athleteIds, sections, null);
    }

    public EditFreeTextTrainingRequest {
        name = name == null ? null : name.strip();
    }
}

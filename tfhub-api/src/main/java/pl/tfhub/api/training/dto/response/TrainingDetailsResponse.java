package pl.tfhub.api.training.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDate;
import java.util.List;
import pl.tfhub.api.training.enums.TrainingStatus;
import pl.tfhub.api.training.enums.TrainingAssignmentType;
import pl.tfhub.api.training.enums.TrainingType;
import pl.tfhub.api.traininggroup.dto.response.TrainingGroupResponse;

public record TrainingDetailsResponse(
        long id,
        String name,
        LocalDate trainingDate,
        TrainingStatus status,
        TrainingGroupResponse trainingGroup,
        TrainingAssignmentType assignmentType,
        List<TrainingAssignedAthleteResponse> assignedAthletes,
        List<TrainingDefinitionDetailsResponse> definitions,
        TrainingType trainingType,
        List<TrainingSectionDetailsResponse> sections,
        @JsonInclude(JsonInclude.Include.NON_NULL) String equipment) {

    public TrainingDetailsResponse(long id, String name, LocalDate trainingDate, TrainingStatus status,
            TrainingGroupResponse trainingGroup, TrainingAssignmentType assignmentType,
            List<TrainingAssignedAthleteResponse> assignedAthletes,
            List<TrainingDefinitionDetailsResponse> definitions, TrainingType trainingType,
            List<TrainingSectionDetailsResponse> sections) {
        this(id, name, trainingDate, status, trainingGroup, assignmentType, assignedAthletes, definitions,
                trainingType, sections, null);
    }
}

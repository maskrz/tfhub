package pl.tfhub.api.training.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDate;
import java.util.List;
import pl.tfhub.api.training.enums.TrainingAssignmentType;
import pl.tfhub.api.training.enums.TrainingType;
import pl.tfhub.api.traininggroup.dto.response.TrainingGroupResponse;

public record TrainingPrintResponse(List<Item> trainings) {
    public record Item(long id, String name, LocalDate trainingDate, TrainingType trainingType,
            TrainingAssignmentType assignmentType, TrainingGroupResponse trainingGroup,
            List<Athlete> assignedAthletes, List<Definition> definitions, List<Section> sections,
            @JsonInclude(JsonInclude.Include.NON_NULL) String equipment) {
    }

    public record Athlete(String firstName, String lastName) {
    }

    public record Definition(String name, int sequenceNumber, List<Segment> segments) {
    }

    public record Segment(int sequenceNumber, int distanceM, Integer restAfterSeconds) {
    }

    public record Section(String title, String content, int sequenceNumber) {
    }
}

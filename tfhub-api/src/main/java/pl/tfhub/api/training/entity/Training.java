package pl.tfhub.api.training.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import pl.tfhub.api.training.enums.TrainingStatus;
import pl.tfhub.api.training.enums.TrainingAssignmentType;
import pl.tfhub.api.training.enums.TrainingType;
import pl.tfhub.api.traininggroup.entity.TrainingGroup;

import java.time.LocalDate;
import java.util.Objects;

@Entity
@Table(name = "training_sessions", indexes = {
        @Index(name = "idx_training_sessions_group_status_date",
                columnList = "training_group_id, status, training_date, id")
})
public class Training {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 255)
    @Column(nullable = false, length = 255)
    private String name;

    @NotNull
    @Column(name = "training_date", nullable = false)
    private LocalDate trainingDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "training_group_id", nullable = true,
            foreignKey = @ForeignKey(name = "fk_training_sessions_group"))
    private TrainingGroup trainingGroup;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TrainingStatus status = TrainingStatus.PLANNED;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "training_type", nullable = false, length = 20)
    private TrainingType trainingType = TrainingType.TEMPO;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "assignment_type", nullable = false, length = 20)
    private TrainingAssignmentType assignmentType = TrainingAssignmentType.GROUP;

    protected Training() {
    }

    public Training(String name, LocalDate trainingDate, TrainingGroup trainingGroup) {
        this(name, trainingDate, trainingGroup, TrainingType.TEMPO);
    }

    public Training(
            String name,
            LocalDate trainingDate,
            TrainingGroup trainingGroup,
            TrainingType trainingType) {
        this(name, trainingDate, trainingGroup, trainingType, TrainingAssignmentType.GROUP);
    }

    public Training(
            String name,
            LocalDate trainingDate,
            TrainingGroup trainingGroup,
            TrainingType trainingType,
            TrainingAssignmentType assignmentType) {
        setName(name);
        this.trainingDate = Objects.requireNonNull(trainingDate);
        this.trainingType = Objects.requireNonNull(trainingType);
        changeAssignment(assignmentType, trainingGroup);
    }

    public void updateDetails(String name, LocalDate trainingDate) {
        setName(name);
        this.trainingDate = Objects.requireNonNull(trainingDate);
    }

    public void markCompleted() {
        this.status = TrainingStatus.COMPLETED;
    }

    public void changeAssignment(TrainingAssignmentType assignmentType, TrainingGroup trainingGroup) {
        Objects.requireNonNull(assignmentType);
        if (assignmentType == TrainingAssignmentType.GROUP) {
            this.trainingGroup = Objects.requireNonNull(trainingGroup);
        } else {
            if (trainingType != TrainingType.FREE_TEXT || trainingGroup != null) {
                throw new IllegalArgumentException("Only FREE_TEXT trainings can be assigned to athletes");
            }
            this.trainingGroup = null;
        }
        this.assignmentType = assignmentType;
    }

    private void setName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Training name must not be blank");
        }
        this.name = name;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public LocalDate getTrainingDate() {
        return trainingDate;
    }

    public TrainingGroup getTrainingGroup() {
        return trainingGroup;
    }

    public TrainingStatus getStatus() {
        return status;
    }

    public TrainingType getTrainingType() {
        return trainingType;
    }

    public TrainingAssignmentType getAssignmentType() {
        return assignmentType;
    }
}

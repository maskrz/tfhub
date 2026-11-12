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

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "training_group_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_training_sessions_group"))
    private TrainingGroup trainingGroup;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TrainingStatus status = TrainingStatus.PLANNED;

    protected Training() {
    }

    public Training(String name, LocalDate trainingDate, TrainingGroup trainingGroup) {
        setName(name);
        this.trainingDate = Objects.requireNonNull(trainingDate);
        this.trainingGroup = Objects.requireNonNull(trainingGroup);
    }

    public void updateDetails(String name, LocalDate trainingDate) {
        setName(name);
        this.trainingDate = Objects.requireNonNull(trainingDate);
    }

    public void markCompleted() {
        this.status = TrainingStatus.COMPLETED;
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
}

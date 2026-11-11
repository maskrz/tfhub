package pl.tfhub.api.training.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.Objects;

@Entity
@Table(name = "training_segments",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_training_segments_definition_sequence",
                        columnNames = {"training_definition_id", "sequence_number"}),
                @UniqueConstraint(name = "uq_training_segments_id_definition",
                        columnNames = {"id", "training_definition_id"})
        },
        indexes = @Index(name = "idx_training_segments_definition", columnList = "training_definition_id"))
public class TrainingSegment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "training_definition_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_training_segments_definition"))
    private TrainingDefinition trainingDefinition;

    @Positive
    @Column(name = "sequence_number", nullable = false)
    private int sequenceNumber;

    @Positive
    @Column(name = "distance_m", nullable = false)
    private int distanceM;

    @PositiveOrZero
    @Column(name = "rest_after_seconds")
    private Integer restAfterSeconds;

    protected TrainingSegment() {
    }

    public TrainingSegment(TrainingDefinition trainingDefinition, int sequenceNumber,
                           int distanceM, Integer restAfterSeconds) {
        this.trainingDefinition = Objects.requireNonNull(trainingDefinition);
        update(sequenceNumber, distanceM, restAfterSeconds);
    }

    public void update(int sequenceNumber, int distanceM, Integer restAfterSeconds) {
        if (sequenceNumber <= 0 || distanceM <= 0
                || (restAfterSeconds != null && restAfterSeconds < 0)) {
            throw new IllegalArgumentException("Invalid training segment values");
        }
        this.sequenceNumber = sequenceNumber;
        this.distanceM = distanceM;
        this.restAfterSeconds = restAfterSeconds;
    }

    public Long getId() {
        return id;
    }

    public TrainingDefinition getTrainingDefinition() {
        return trainingDefinition;
    }

    public int getSequenceNumber() {
        return sequenceNumber;
    }

    public int getDistanceM() {
        return distanceM;
    }

    public Integer getRestAfterSeconds() {
        return restAfterSeconds;
    }
}

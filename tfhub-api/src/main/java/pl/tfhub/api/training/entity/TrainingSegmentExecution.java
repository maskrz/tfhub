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
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import pl.tfhub.api.training.enums.TrainingExecutionStatus;

import java.math.BigDecimal;
import java.util.Objects;

@Entity
@Table(name = "training_segment_executions",
        uniqueConstraints = @UniqueConstraint(name = "uq_training_executions_participation_segment",
                columnNames = {"participation_id", "training_segment_id"}),
        indexes = {
                @Index(name = "idx_training_executions_segment", columnList = "training_segment_id"),
                @Index(name = "idx_training_executions_definition", columnList = "training_definition_id")
        })
public class TrainingSegmentExecution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "participation_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_training_executions_participation"))
    private AthleteTrainingParticipation participation;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "training_segment_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_training_executions_segment"))
    private TrainingSegment trainingSegment;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "training_definition_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_training_executions_definition"))
    private TrainingDefinition trainingDefinition;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private TrainingExecutionStatus status;

    @DecimalMin(value = "0.0")
    @Digits(integer = 9, fraction = 3)
    @Column(name = "measured_result", precision = 12, scale = 3)
    private BigDecimal measuredResult;

    @Column(name = "uses_spikes", nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
    private boolean usesSpikes;

    protected TrainingSegmentExecution() {
    }

    public TrainingSegmentExecution(AthleteTrainingParticipation participation, TrainingSegment trainingSegment,
                                    TrainingDefinition trainingDefinition, TrainingExecutionStatus status,
                                    BigDecimal measuredResult, boolean usesSpikes) {
        this.participation = Objects.requireNonNull(participation);
        this.trainingSegment = Objects.requireNonNull(trainingSegment);
        this.trainingDefinition = Objects.requireNonNull(trainingDefinition);
        update(status, measuredResult, usesSpikes);
    }

    public void update(TrainingExecutionStatus status, BigDecimal measuredResult, boolean usesSpikes) {
        Objects.requireNonNull(status);
        if ((status == TrainingExecutionStatus.RECORDED) != (measuredResult != null)) {
            throw new IllegalArgumentException("A measured result is required only for RECORDED executions");
        }
        if (measuredResult != null && measuredResult.signum() < 0) {
            throw new IllegalArgumentException("Measured result must not be negative");
        }
        this.status = status;
        this.measuredResult = measuredResult;
        this.usesSpikes = usesSpikes;
    }

    public Long getId() {
        return id;
    }

    public AthleteTrainingParticipation getParticipation() {
        return participation;
    }

    public TrainingSegment getTrainingSegment() {
        return trainingSegment;
    }

    public TrainingDefinition getTrainingDefinition() {
        return trainingDefinition;
    }

    public TrainingExecutionStatus getStatus() {
        return status;
    }

    public BigDecimal getMeasuredResult() {
        return measuredResult;
    }

    public boolean isUsesSpikes() {
        return usesSpikes;
    }
}

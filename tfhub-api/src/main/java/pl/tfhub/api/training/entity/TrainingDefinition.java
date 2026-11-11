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
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.Objects;

@Entity
@Table(name = "training_definitions",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_training_definitions_session_sequence",
                        columnNames = {"training_session_id", "sequence_number"}),
                @UniqueConstraint(name = "uq_training_definitions_id_session",
                        columnNames = {"id", "training_session_id"})
        },
        indexes = @Index(name = "idx_training_definitions_session", columnList = "training_session_id"))
public class TrainingDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "training_session_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_training_definitions_session"))
    private Training training;

    @NotBlank
    @Size(max = 255)
    @Column(nullable = false, length = 255)
    private String name;

    @Positive
    @Column(name = "sequence_number", nullable = false)
    private int sequenceNumber;

    protected TrainingDefinition() {
    }

    public TrainingDefinition(Training training, String name, int sequenceNumber) {
        this.training = Objects.requireNonNull(training);
        setName(name);
        setSequenceNumber(sequenceNumber);
    }

    public void update(String name, int sequenceNumber) {
        setName(name);
        setSequenceNumber(sequenceNumber);
    }

    private void setName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Training definition name must not be blank");
        }
        this.name = name;
    }

    private void setSequenceNumber(int sequenceNumber) {
        if (sequenceNumber <= 0) {
            throw new IllegalArgumentException("Sequence number must be positive");
        }
        this.sequenceNumber = sequenceNumber;
    }

    public Long getId() {
        return id;
    }

    public Training getTraining() {
        return training;
    }

    public String getName() {
        return name;
    }

    public int getSequenceNumber() {
        return sequenceNumber;
    }
}

package pl.tfhub.api.training.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.Objects;

@Entity
@Table(name = "training_sections")
public class TrainingSection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "training_session_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_training_sections_session"))
    private Training training;

    @NotBlank
    @Column(nullable = false, columnDefinition = "TEXT")
    private String title;

    @NotBlank
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @NotNull
    @Positive
    @Column(name = "sequence_number", nullable = false)
    private Integer sequenceNumber;

    protected TrainingSection() {
    }

    public TrainingSection(Training training, String title, String content, int sequenceNumber) {
        this(training, title, content, sequenceNumber, false);
    }

    public TrainingSection(
            Training training,
            String title,
            String content,
            int sequenceNumber,
            boolean preserveText) {
        this.training = Objects.requireNonNull(training);
        update(title, content, sequenceNumber, preserveText);
    }

    public void update(String title, String content, int sequenceNumber) {
        update(title, content, sequenceNumber, false);
    }

    private void update(String title, String content, int sequenceNumber, boolean preserveText) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Training section title must not be blank");
        }
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("Training section content must not be blank");
        }
        if (sequenceNumber <= 0) {
            throw new IllegalArgumentException("Training section sequence number must be positive");
        }
        this.title = preserveText ? title : title.strip();
        this.content = preserveText ? content : content.strip();
        this.sequenceNumber = sequenceNumber;
    }

    public Long getId() {
        return id;
    }

    public Training getTraining() {
        return training;
    }

    public String getTitle() {
        return title;
    }

    public String getContent() {
        return content;
    }

    public Integer getSequenceNumber() {
        return sequenceNumber;
    }
}

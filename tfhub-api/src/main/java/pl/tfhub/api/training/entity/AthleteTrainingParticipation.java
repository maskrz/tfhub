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
import pl.tfhub.api.athlete.entity.Athlete;

import java.util.Objects;

@Entity
@Table(name = "training_athlete_participations",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_training_participations_session_athlete",
                        columnNames = {"training_session_id", "athlete_id"}),
                @UniqueConstraint(name = "uq_training_participations_id_definition",
                        columnNames = {"id", "training_definition_id"})
        },
        indexes = {
                @Index(name = "idx_training_participations_definition", columnList = "training_definition_id"),
                @Index(name = "idx_training_participations_athlete", columnList = "athlete_id")
        })
public class AthleteTrainingParticipation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "training_session_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_training_participations_session"))
    private Training training;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "training_definition_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_training_participations_definition"))
    private TrainingDefinition trainingDefinition;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "athlete_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_training_participations_athlete"))
    private Athlete athlete;

    protected AthleteTrainingParticipation() {
    }

    public AthleteTrainingParticipation(Training training, TrainingDefinition trainingDefinition, Athlete athlete) {
        this.training = Objects.requireNonNull(training);
        this.trainingDefinition = Objects.requireNonNull(trainingDefinition);
        this.athlete = Objects.requireNonNull(athlete);
    }

    public Long getId() {
        return id;
    }

    public Training getTraining() {
        return training;
    }

    public TrainingDefinition getTrainingDefinition() {
        return trainingDefinition;
    }

    public Athlete getAthlete() {
        return athlete;
    }
}

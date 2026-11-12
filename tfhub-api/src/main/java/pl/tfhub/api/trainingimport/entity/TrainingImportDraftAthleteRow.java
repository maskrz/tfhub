package pl.tfhub.api.trainingimport.entity;

import java.util.Map;
import java.util.Objects;
import java.util.Collections;
import java.util.LinkedHashMap;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import pl.tfhub.api.athlete.entity.Athlete;
import pl.tfhub.api.trainingimport.enums.TrainingImportAthleteMatchStatus;

@Entity
@Table(name = "training_import_draft_athlete_rows",
		indexes = @Index(name = "idx_training_import_draft_athlete_rows_definition", columnList = "definition_id"))
public class TrainingImportDraftAthleteRow {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "draft_id", nullable = false)
	private TrainingImportDraft draft;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "definition_id", nullable = false,
			foreignKey = @ForeignKey(name = "fk_training_import_draft_athlete_rows_definition_draft"))
	private TrainingImportDraftDefinition definition;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "athlete_id",
			foreignKey = @ForeignKey(name = "fk_training_import_draft_athlete_rows_athlete"))
	private Athlete athlete;

	@Column(name = "recognized_name", nullable = false, columnDefinition = "TEXT")
	private String recognizedName;

	@Enumerated(EnumType.STRING)
	@Column(name = "match_status", nullable = false, length = 16)
	private TrainingImportAthleteMatchStatus matchStatus;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "recognition_metadata", nullable = false, columnDefinition = "jsonb")
	private Map<String, Object> recognitionMetadata = Map.of();

	protected TrainingImportDraftAthleteRow() {
	}

	public TrainingImportDraftAthleteRow(
			TrainingImportDraft draft,
			TrainingImportDraftDefinition definition,
			Athlete athlete,
			String recognizedName,
			TrainingImportAthleteMatchStatus matchStatus,
			Map<String, Object> recognitionMetadata) {
		this.draft = Objects.requireNonNull(draft);
		this.definition = Objects.requireNonNull(definition);
		this.recognizedName = Objects.requireNonNull(recognizedName);
		updateMatch(athlete, matchStatus);
		setRecognitionMetadata(recognitionMetadata);
	}

	public void moveToDefinition(TrainingImportDraftDefinition definition) {
		this.definition = Objects.requireNonNull(definition);
	}

	public void updateMatch(Athlete athlete, TrainingImportAthleteMatchStatus matchStatus) {
		Objects.requireNonNull(matchStatus);
		if ((matchStatus == TrainingImportAthleteMatchStatus.MATCHED) != (athlete != null)) {
			throw new IllegalArgumentException("Only a matched athlete row may reference an athlete");
		}
		this.athlete = athlete;
		this.matchStatus = matchStatus;
	}

	public void setRecognitionMetadata(Map<String, Object> recognitionMetadata) {
		this.recognitionMetadata = recognitionMetadata == null
				? Map.of()
				: Collections.unmodifiableMap(new LinkedHashMap<>(recognitionMetadata));
	}

	public Long getId() {
		return id;
	}

	public TrainingImportDraft getDraft() {
		return draft;
	}

	public TrainingImportDraftDefinition getDefinition() {
		return definition;
	}

	public Athlete getAthlete() {
		return athlete;
	}

	public String getRecognizedName() {
		return recognizedName;
	}

	public TrainingImportAthleteMatchStatus getMatchStatus() {
		return matchStatus;
	}

	public Map<String, Object> getRecognitionMetadata() {
		return recognitionMetadata;
	}
}

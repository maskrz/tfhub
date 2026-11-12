package pl.tfhub.api.trainingimport.entity;

import java.math.BigDecimal;
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
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import pl.tfhub.api.training.enums.TrainingExecutionStatus;

@Entity
@Table(name = "training_import_draft_executions",
		uniqueConstraints = @UniqueConstraint(name = "uq_training_import_draft_executions_row_segment",
				columnNames = {"athlete_row_id", "segment_id"}),
		indexes = {
				@Index(name = "idx_training_import_draft_executions_draft_definition",
						columnList = "draft_id, definition_id"),
				@Index(name = "idx_training_import_draft_executions_segment", columnList = "segment_id")
		})
public class TrainingImportDraftExecution {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "draft_id", nullable = false)
	private TrainingImportDraft draft;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "definition_id", nullable = false)
	private TrainingImportDraftDefinition definition;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "athlete_row_id", nullable = false,
			foreignKey = @ForeignKey(name = "fk_training_import_draft_executions_athlete_row"))
	private TrainingImportDraftAthleteRow athleteRow;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "segment_id", nullable = false,
			foreignKey = @ForeignKey(name = "fk_training_import_draft_executions_segment"))
	private TrainingImportDraftSegment segment;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 40)
	private TrainingExecutionStatus status;

	@DecimalMin("0.0")
	@Digits(integer = 9, fraction = 3)
	@Column(name = "measured_result", precision = 12, scale = 3)
	private BigDecimal measuredResult;

	@Column(name = "uses_spikes")
	private Boolean usesSpikes;

	@Column(name = "recognized_text", columnDefinition = "TEXT")
	private String recognizedText;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "recognition_metadata", nullable = false, columnDefinition = "jsonb")
	private Map<String, Object> recognitionMetadata = Map.of();

	protected TrainingImportDraftExecution() {
	}

	public TrainingImportDraftExecution(
			TrainingImportDraft draft,
			TrainingImportDraftDefinition definition,
			TrainingImportDraftAthleteRow athleteRow,
			TrainingImportDraftSegment segment,
			TrainingExecutionStatus status,
			BigDecimal measuredResult,
			Boolean usesSpikes,
			String recognizedText,
			Map<String, Object> recognitionMetadata) {
		this.draft = Objects.requireNonNull(draft);
		this.definition = Objects.requireNonNull(definition);
		this.athleteRow = Objects.requireNonNull(athleteRow);
		this.segment = Objects.requireNonNull(segment);
		this.recognizedText = recognizedText;
		setRecognitionMetadata(recognitionMetadata);
		update(status, measuredResult, usesSpikes);
	}

	public void update(TrainingExecutionStatus status, BigDecimal measuredResult, Boolean usesSpikes) {
		Objects.requireNonNull(status);
		if ((status == TrainingExecutionStatus.RECORDED) != (measuredResult != null)) {
			throw new IllegalArgumentException("A measured result is required only for RECORDED executions");
		}
		if (measuredResult != null && (measuredResult.signum() < 0
				|| measuredResult.precision() - measuredResult.scale() > 9
				|| measuredResult.scale() > 3)) {
			throw new IllegalArgumentException("Measured result must fit NUMERIC(12,3) and be non-negative");
		}
		this.status = status;
		this.measuredResult = measuredResult;
		this.usesSpikes = usesSpikes;
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

	public TrainingImportDraftAthleteRow getAthleteRow() {
		return athleteRow;
	}

	public TrainingImportDraftSegment getSegment() {
		return segment;
	}

	public TrainingExecutionStatus getStatus() {
		return status;
	}

	public BigDecimal getMeasuredResult() {
		return measuredResult;
	}

	public Boolean getUsesSpikes() {
		return usesSpikes;
	}

	public String getRecognizedText() {
		return recognizedText;
	}

	public Map<String, Object> getRecognitionMetadata() {
		return recognitionMetadata;
	}
}

package pl.tfhub.api.trainingimport.entity;

import java.util.Map;
import java.util.Objects;
import java.util.Collections;
import java.util.LinkedHashMap;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "training_import_draft_segments",
		uniqueConstraints = {
				@UniqueConstraint(name = "uq_training_import_draft_segments_definition_sequence",
						columnNames = {"definition_id", "sequence_number"}),
				@UniqueConstraint(name = "uq_training_import_draft_segments_id_draft_definition",
						columnNames = {"id", "draft_id", "definition_id"})
		},
		indexes = @Index(name = "idx_training_import_draft_segments_draft_definition",
				columnList = "draft_id, definition_id, sequence_number"))
public class TrainingImportDraftSegment {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "draft_id", nullable = false)
	private TrainingImportDraft draft;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "definition_id", nullable = false,
			foreignKey = @ForeignKey(name = "fk_training_import_draft_segments_definition_draft"))
	private TrainingImportDraftDefinition definition;

	@Column(name = "sequence_number", nullable = false)
	private int sequenceNumber;

	@Column(name = "distance_m")
	private Integer distanceM;

	@Column(name = "rest_after_seconds")
	private Integer restAfterSeconds;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "recognition_metadata", nullable = false, columnDefinition = "jsonb")
	private Map<String, Object> recognitionMetadata = Map.of();

	protected TrainingImportDraftSegment() {
	}

	public TrainingImportDraftSegment(
			TrainingImportDraft draft,
			TrainingImportDraftDefinition definition,
			int sequenceNumber,
			Integer distanceM,
			Integer restAfterSeconds,
			Map<String, Object> recognitionMetadata) {
		this.draft = Objects.requireNonNull(draft);
		this.definition = Objects.requireNonNull(definition);
		update(sequenceNumber, distanceM, restAfterSeconds);
		setRecognitionMetadata(recognitionMetadata);
	}

	public void update(int sequenceNumber, Integer distanceM, Integer restAfterSeconds) {
		if (sequenceNumber <= 0 || (distanceM != null && distanceM <= 0)
				|| (restAfterSeconds != null && restAfterSeconds < 0)) {
			throw new IllegalArgumentException("Invalid training import draft segment");
		}
		this.sequenceNumber = sequenceNumber;
		this.distanceM = distanceM;
		this.restAfterSeconds = restAfterSeconds;
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

	public int getSequenceNumber() {
		return sequenceNumber;
	}

	public Integer getDistanceM() {
		return distanceM;
	}

	public Integer getRestAfterSeconds() {
		return restAfterSeconds;
	}

	public Map<String, Object> getRecognitionMetadata() {
		return recognitionMetadata;
	}
}

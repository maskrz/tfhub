package pl.tfhub.api.trainingimport.entity;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "training_import_draft_sections")
public class TrainingImportDraftSection {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "draft_id", nullable = false,
			foreignKey = @ForeignKey(name = "fk_training_import_draft_sections_draft"))
	private TrainingImportDraft draft;

	@Column(name = "sequence_number", nullable = false)
	private int sequenceNumber;

	@Column(nullable = false, length = 255)
	private String title;

	@Column(nullable = false, columnDefinition = "text")
	private String content;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "recognition_metadata", nullable = false, columnDefinition = "jsonb")
	private Map<String, Object> recognitionMetadata = Map.of();

	protected TrainingImportDraftSection() {
	}

	public TrainingImportDraftSection(
			TrainingImportDraft draft,
			int sequenceNumber,
			String title,
			String content,
			Map<String, Object> recognitionMetadata) {
		this.draft = Objects.requireNonNull(draft);
		update(sequenceNumber, title, content);
		setRecognitionMetadata(recognitionMetadata);
	}

	public void update(int sequenceNumber, String title, String content) {
		if (sequenceNumber <= 0 || title == null || title.isBlank()
				|| title.length() > 255 || content == null) {
			throw new IllegalArgumentException("Invalid training import draft section");
		}
		this.sequenceNumber = sequenceNumber;
		this.title = title.strip();
		this.content = content;
	}

	public void setRecognitionMetadata(Map<String, Object> recognitionMetadata) {
		this.recognitionMetadata = recognitionMetadata == null
				? Map.of()
				: Collections.unmodifiableMap(new LinkedHashMap<>(recognitionMetadata));
	}

	public Long getId() {
		return id;
	}

	public int getSequenceNumber() {
		return sequenceNumber;
	}

	public String getTitle() {
		return title;
	}

	public String getContent() {
		return content;
	}

	public Map<String, Object> getRecognitionMetadata() {
		return recognitionMetadata;
	}
}

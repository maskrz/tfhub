package pl.tfhub.api.trainingimport.entity;

import java.time.LocalDate;
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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "training_import_drafts")
public class TrainingImportDraft {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "import_id", nullable = false, unique = true,
			foreignKey = @ForeignKey(name = "fk_training_import_drafts_import"))
	private TrainingImport trainingImport;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "generated_by_attempt_id", nullable = false,
			foreignKey = @ForeignKey(name = "fk_training_import_drafts_generated_attempt"))
	private TrainingImportAttempt generatedByAttempt;

	@Column(nullable = false, length = 255)
	private String name;

	@Column(name = "training_date", nullable = false)
	private LocalDate trainingDate;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "recognition_metadata", nullable = false, columnDefinition = "jsonb")
	private Map<String, Object> recognitionMetadata = Map.of();

	protected TrainingImportDraft() {
	}

	public TrainingImportDraft(
			TrainingImport trainingImport,
			TrainingImportAttempt generatedByAttempt,
			String name,
			LocalDate trainingDate,
			Map<String, Object> recognitionMetadata) {
		this.trainingImport = Objects.requireNonNull(trainingImport);
		this.generatedByAttempt = Objects.requireNonNull(generatedByAttempt);
		update(name, trainingDate);
		setRecognitionMetadata(recognitionMetadata);
	}

	public void update(String name, LocalDate trainingDate) {
		if (name == null || name.isBlank()) {
			throw new IllegalArgumentException("Training name must not be blank");
		}
		this.name = name.strip();
		this.trainingDate = Objects.requireNonNull(trainingDate);
	}

	public void setRecognitionMetadata(Map<String, Object> recognitionMetadata) {
		this.recognitionMetadata = recognitionMetadata == null
				? Map.of()
				: Collections.unmodifiableMap(new LinkedHashMap<>(recognitionMetadata));
	}

	public Long getId() {
		return id;
	}

	public TrainingImport getTrainingImport() {
		return trainingImport;
	}

	public TrainingImportAttempt getGeneratedByAttempt() {
		return generatedByAttempt;
	}

	public String getName() {
		return name;
	}

	public LocalDate getTrainingDate() {
		return trainingDate;
	}

	public Map<String, Object> getRecognitionMetadata() {
		return recognitionMetadata;
	}
}

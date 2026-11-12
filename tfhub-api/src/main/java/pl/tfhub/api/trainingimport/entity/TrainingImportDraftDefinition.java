package pl.tfhub.api.trainingimport.entity;

import java.util.Objects;
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

@Entity
@Table(name = "training_import_draft_definitions",
		uniqueConstraints = {
				@UniqueConstraint(name = "uq_training_import_draft_definitions_sequence",
						columnNames = {"draft_id", "sequence_number"}),
				@UniqueConstraint(name = "uq_training_import_draft_definitions_id_draft",
						columnNames = {"id", "draft_id"})
		},
		indexes = @Index(name = "idx_training_import_draft_definitions_draft", columnList = "draft_id"))
public class TrainingImportDraftDefinition {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "draft_id", nullable = false,
			foreignKey = @ForeignKey(name = "fk_training_import_draft_definitions_draft"))
	private TrainingImportDraft draft;

	@Column(nullable = false, length = 255)
	private String name;

	@Column(name = "sequence_number", nullable = false)
	private int sequenceNumber;

	protected TrainingImportDraftDefinition() {
	}

	public TrainingImportDraftDefinition(TrainingImportDraft draft, String name, int sequenceNumber) {
		this.draft = Objects.requireNonNull(draft);
		update(name, sequenceNumber);
	}

	public void update(String name, int sequenceNumber) {
		if (name == null || name.isBlank() || sequenceNumber <= 0) {
			throw new IllegalArgumentException("Invalid training import draft definition");
		}
		this.name = name.strip();
		this.sequenceNumber = sequenceNumber;
	}

	public Long getId() {
		return id;
	}

	public TrainingImportDraft getDraft() {
		return draft;
	}

	public String getName() {
		return name;
	}

	public int getSequenceNumber() {
		return sequenceNumber;
	}
}

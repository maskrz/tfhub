package pl.tfhub.api.trainingimport.entity;

import java.time.OffsetDateTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import pl.tfhub.api.trainingimport.enums.TrainingImportAttemptStatus;
import pl.tfhub.api.user.entity.User;

@Entity
@Table(name = "training_import_attempts",
		uniqueConstraints = {
				@UniqueConstraint(name = "uq_training_import_attempts_import_number",
						columnNames = {"import_id", "attempt_number"}),
				@UniqueConstraint(name = "uq_training_import_attempts_id_import",
						columnNames = {"id", "import_id"})
		})
public class TrainingImportAttempt {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "import_id", nullable = false,
			foreignKey = @ForeignKey(name = "fk_training_import_attempts_import"))
	private TrainingImport trainingImport;

	@Column(name = "attempt_number", nullable = false)
	private int attemptNumber;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "requested_by_user_id",
			foreignKey = @ForeignKey(name = "fk_training_import_attempts_requested_by"))
	private User requestedBy;

	@Column(name = "model_name", nullable = false, length = 100)
	private String modelName;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private TrainingImportAttemptStatus status;

	@Column(name = "started_at", nullable = false)
	private OffsetDateTime startedAt;

	@Column(name = "finished_at")
	private OffsetDateTime finishedAt;

	@Column(name = "raw_model_response", columnDefinition = "TEXT")
	private String rawModelResponse;

	@Column(name = "failure_code", length = 100)
	private String failureCode;

	@Column(name = "failure_message", columnDefinition = "TEXT")
	private String failureMessage;

	protected TrainingImportAttempt() {
	}

	public TrainingImportAttempt(
			TrainingImport trainingImport,
			int attemptNumber,
			User requestedBy,
			String modelName,
			TrainingImportAttemptStatus status,
			OffsetDateTime startedAt,
			OffsetDateTime finishedAt,
			String rawModelResponse,
			String failureCode,
			String failureMessage) {
		this.trainingImport = trainingImport;
		this.attemptNumber = attemptNumber;
		this.requestedBy = requestedBy;
		this.modelName = modelName;
		this.status = status;
		this.startedAt = startedAt;
		this.finishedAt = finishedAt;
		this.rawModelResponse = rawModelResponse;
		this.failureCode = failureCode;
		this.failureMessage = failureMessage;
	}

	public Long getId() {
		return id;
	}

	public TrainingImport getTrainingImport() {
		return trainingImport;
	}

	public int getAttemptNumber() {
		return attemptNumber;
	}

	public User getRequestedBy() {
		return requestedBy;
	}

	public String getModelName() {
		return modelName;
	}

	public TrainingImportAttemptStatus getStatus() {
		return status;
	}

	public OffsetDateTime getStartedAt() {
		return startedAt;
	}

	public OffsetDateTime getFinishedAt() {
		return finishedAt;
	}

	public String getRawModelResponse() {
		return rawModelResponse;
	}

	public String getFailureCode() {
		return failureCode;
	}

	public String getFailureMessage() {
		return failureMessage;
	}

	public void markSucceeded(OffsetDateTime finishedAt, String rawModelResponse) {
		requireProcessing();
		this.status = TrainingImportAttemptStatus.SUCCEEDED;
		this.finishedAt = finishedAt;
		this.rawModelResponse = rawModelResponse;
		this.failureCode = null;
		this.failureMessage = null;
	}

	public void markFailed(
			OffsetDateTime finishedAt,
			String rawModelResponse,
			String failureCode,
			String failureMessage) {
		requireProcessing();
		this.status = TrainingImportAttemptStatus.FAILED;
		this.finishedAt = finishedAt;
		this.rawModelResponse = rawModelResponse;
		this.failureCode = failureCode;
		this.failureMessage = failureMessage;
	}

	private void requireProcessing() {
		if (status != TrainingImportAttemptStatus.PROCESSING || finishedAt != null) {
			throw new IllegalStateException("Only a processing attempt can be completed");
		}
	}
}

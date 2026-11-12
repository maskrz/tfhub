package pl.tfhub.api.trainingimport.entity;

import java.time.OffsetDateTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import pl.tfhub.api.traininggroup.entity.TrainingGroup;
import pl.tfhub.api.trainingimport.enums.TrainingImportStatus;
import pl.tfhub.api.user.entity.User;

@Entity
@Table(name = "training_imports")
public class TrainingImport {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "training_group_id", nullable = false, updatable = false)
	private TrainingGroup trainingGroup;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "initiated_by_user_id", nullable = false, updatable = false)
	private User initiatedBy;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private TrainingImportStatus status;

	@Column(name = "created_at", nullable = false, updatable = false)
	private OffsetDateTime createdAt;

	@Column(name = "possible_duplicate", nullable = false, updatable = false)
	private boolean possibleDuplicate;

	@OneToOne(mappedBy = "trainingImport", fetch = FetchType.LAZY, optional = false)
	private TrainingImportSourceImage sourceImage;

	protected TrainingImport() {
	}

	public TrainingImport(
			TrainingGroup trainingGroup,
			User initiatedBy,
			OffsetDateTime createdAt,
			boolean possibleDuplicate) {
		this.trainingGroup = trainingGroup;
		this.initiatedBy = initiatedBy;
		this.status = TrainingImportStatus.PENDING;
		this.createdAt = createdAt;
		this.possibleDuplicate = possibleDuplicate;
	}

	public Long getId() {
		return id;
	}

	public TrainingGroup getTrainingGroup() {
		return trainingGroup;
	}

	public User getInitiatedBy() {
		return initiatedBy;
	}

	public TrainingImportStatus getStatus() {
		return status;
	}

	public OffsetDateTime getCreatedAt() {
		return createdAt;
	}

	public boolean isPossibleDuplicate() {
		return possibleDuplicate;
	}

	public TrainingImportSourceImage getSourceImage() {
		return sourceImage;
	}

	public void changeStatus(TrainingImportStatus status) {
		this.status = status;
	}

	void attachSourceImage(TrainingImportSourceImage sourceImage) {
		this.sourceImage = sourceImage;
	}
}

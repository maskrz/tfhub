package pl.tfhub.api.competition.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import pl.tfhub.api.competition.enums.CompetitionSyncMode;
import pl.tfhub.api.competition.enums.CompetitionSyncStatus;

@Entity
@Table(name = "competition_sync_runs")
public class CompetitionSyncRun {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "athlete_id", nullable = false)
	private Long athleteId;

	@Enumerated(EnumType.STRING)
	@Column(name = "sync_mode", nullable = false, length = 30)
	private CompetitionSyncMode mode;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private CompetitionSyncStatus status;

	@Column(name = "requested_at", nullable = false)
	private Instant requestedAt = Instant.now();

	@Column(name = "started_at")
	private Instant startedAt;

	@Column(name = "finished_at")
	private Instant finishedAt;

	@Column(name = "error_code", length = 60)
	private String errorCode;

	@Column(name = "error_message", length = 500)
	private String errorMessage;

	protected CompetitionSyncRun() {
	}

	public CompetitionSyncRun(Long athleteId, CompetitionSyncMode mode) {
		this.athleteId = athleteId;
		this.mode = mode;
		this.status = CompetitionSyncStatus.QUEUED;
	}

	public void start(Instant startedAt) {
		this.status = CompetitionSyncStatus.RUNNING;
		this.startedAt = startedAt;
		this.errorCode = null;
		this.errorMessage = null;
	}

	public void succeed(Instant finishedAt) {
		this.status = CompetitionSyncStatus.SUCCEEDED;
		this.finishedAt = finishedAt;
	}

	public void fail(Instant finishedAt, String errorCode, String errorMessage) {
		this.status = CompetitionSyncStatus.FAILED;
		this.finishedAt = finishedAt;
		this.errorCode = errorCode;
		this.errorMessage = errorMessage;
	}

	public Long getId() {
		return id;
	}

	public Long getAthleteId() {
		return athleteId;
	}

	public CompetitionSyncMode getMode() {
		return mode;
	}

	public CompetitionSyncStatus getStatus() {
		return status;
	}

	public Instant getRequestedAt() {
		return requestedAt;
	}

	public Instant getStartedAt() {
		return startedAt;
	}

	public Instant getFinishedAt() {
		return finishedAt;
	}

	public String getErrorCode() {
		return errorCode;
	}

	public String getErrorMessage() {
		return errorMessage;
	}
}

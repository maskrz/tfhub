package pl.tfhub.api.competition.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.tfhub.api.competition.dto.request.StartCompetitionSyncRequest;
import pl.tfhub.api.competition.dto.response.CompetitionSyncStatusResponse;
import pl.tfhub.api.competition.dto.response.GroupCompetitionSyncResponse;
import pl.tfhub.api.competition.exception.InvalidCompetitionResultIdException;
import pl.tfhub.api.competition.service.CompetitionSyncService;

@RestController
public class CompetitionSyncController {

	private final CompetitionSyncService syncService;

	public CompetitionSyncController(CompetitionSyncService syncService) {
		this.syncService = syncService;
	}

	@PostMapping("/api/athletes/{athleteId}/competition-sync")
	public ResponseEntity<CompetitionSyncStatusResponse> startAthleteSync(
			@PathVariable Long athleteId,
			@Valid @RequestBody StartCompetitionSyncRequest request) {
		validateId(athleteId);
		return ResponseEntity.accepted().body(syncService.startSync(athleteId, request.mode()));
	}

	@GetMapping("/api/athletes/{athleteId}/competition-sync-status")
	public CompetitionSyncStatusResponse getAthleteSyncStatus(@PathVariable Long athleteId) {
		validateId(athleteId);
		return syncService.getLatestStatus(athleteId);
	}

	@GetMapping("/api/competition-syncs/{runId}")
	public CompetitionSyncStatusResponse getRunStatus(@PathVariable String runId) {
		return syncService.getRunStatus(parseId(runId));
	}

	@PostMapping("/api/training-groups/{groupId}/competition-sync")
	public ResponseEntity<GroupCompetitionSyncResponse> startGroupSync(
			@PathVariable Long groupId,
			@Valid @RequestBody StartCompetitionSyncRequest request) {
		validateId(groupId);
		return ResponseEntity.status(HttpStatus.ACCEPTED)
				.body(syncService.startGroupSync(groupId, request.mode()));
	}

	private long parseId(String value) {
		try {
			long id = Long.parseLong(value);
			if (id > 0) {
				return id;
			}
		} catch (NumberFormatException exception) {
			throw new InvalidCompetitionResultIdException();
		}
		throw new InvalidCompetitionResultIdException();
	}

	private void validateId(Long id) {
		if (id <= 0) {
			throw new InvalidCompetitionResultIdException();
		}
	}
}

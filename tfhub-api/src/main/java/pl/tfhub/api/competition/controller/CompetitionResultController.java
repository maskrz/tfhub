package pl.tfhub.api.competition.controller;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.tfhub.api.competition.dto.request.SaveCompetitionResultRequest;
import pl.tfhub.api.competition.dto.response.CompetitionResultResponse;
import pl.tfhub.api.competition.exception.InvalidCompetitionResultIdException;
import pl.tfhub.api.competition.service.CompetitionResultService;

@RestController
public class CompetitionResultController {

	private final CompetitionResultService resultService;

	public CompetitionResultController(CompetitionResultService resultService) {
		this.resultService = resultService;
	}

	@GetMapping("/api/athletes/{athleteId}/competition-results")
	public List<CompetitionResultResponse> getAthleteResults(@PathVariable Long athleteId) {
		if (athleteId <= 0) {
			throw new InvalidCompetitionResultIdException();
		}
		return resultService.getAthleteResults(athleteId);
	}

	@PostMapping("/api/athletes/{athleteId}/competition-results")
	public ResponseEntity<CompetitionResultResponse> createResult(
			@PathVariable Long athleteId,
			@Valid @RequestBody SaveCompetitionResultRequest request) {
		if (athleteId <= 0) {
			throw new InvalidCompetitionResultIdException();
		}
		CompetitionResultResponse response = resultService.createResult(athleteId, request);
		return ResponseEntity.created(URI.create("/api/competition-results/" + response.id())).body(response);
	}

	@GetMapping("/api/competition-results/{id}")
	public CompetitionResultResponse getResult(@PathVariable String id) {
		return resultService.getResult(parseId(id));
	}

	@PutMapping("/api/competition-results/{id}")
	public CompetitionResultResponse updateResult(
			@PathVariable String id,
			@Valid @RequestBody SaveCompetitionResultRequest request) {
		return resultService.updateResult(parseId(id), request);
	}

	@DeleteMapping("/api/competition-results/{id}")
	public ResponseEntity<Void> deleteResult(@PathVariable String id) {
		resultService.deleteResult(parseId(id));
		return ResponseEntity.noContent().build();
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
}

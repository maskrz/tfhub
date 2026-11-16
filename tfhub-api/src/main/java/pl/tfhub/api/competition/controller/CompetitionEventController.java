package pl.tfhub.api.competition.controller;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.tfhub.api.competition.dto.request.ChangeCompetitionEventStatusRequest;
import pl.tfhub.api.competition.dto.request.SaveCompetitionEventRequest;
import pl.tfhub.api.competition.dto.response.CompetitionEventResponse;
import pl.tfhub.api.competition.exception.InvalidCompetitionEventIdException;
import pl.tfhub.api.competition.service.CompetitionEventService;

@RestController
@RequestMapping("/api/competition-events")
public class CompetitionEventController {

	private final CompetitionEventService eventService;

	public CompetitionEventController(CompetitionEventService eventService) {
		this.eventService = eventService;
	}

	@GetMapping
	public List<CompetitionEventResponse> getEvents() {
		return eventService.getEvents();
	}

	@PostMapping
	public ResponseEntity<CompetitionEventResponse> createEvent(
			@Valid @RequestBody SaveCompetitionEventRequest request) {
		CompetitionEventResponse response = eventService.createEvent(request);
		return ResponseEntity.created(URI.create("/api/competition-events/" + response.id())).body(response);
	}

	@PutMapping("/{id}")
	public CompetitionEventResponse updateEvent(
			@PathVariable Long id,
			@Valid @RequestBody SaveCompetitionEventRequest request) {
		validateId(id);
		return eventService.updateEvent(id, request);
	}

	@PatchMapping("/{id}/status")
	public CompetitionEventResponse changeStatus(
			@PathVariable Long id,
			@Valid @RequestBody ChangeCompetitionEventStatusRequest request) {
		validateId(id);
		return eventService.changeStatus(id, request);
	}

	private void validateId(Long id) {
		if (id <= 0) {
			throw new InvalidCompetitionEventIdException();
		}
	}
}

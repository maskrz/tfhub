package pl.tfhub.api.athlete.controller;

import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pl.tfhub.api.athlete.dto.request.ChangeAthleteStatusRequest;
import pl.tfhub.api.athlete.dto.request.CreateAthleteRequest;
import pl.tfhub.api.athlete.dto.request.SearchAthletesRequest;
import pl.tfhub.api.athlete.dto.request.UpdateAthleteRequest;
import pl.tfhub.api.athlete.dto.response.AthleteResponse;
import pl.tfhub.api.athlete.exception.InvalidAthleteIdException;
import pl.tfhub.api.athlete.service.AthleteService;
import pl.tfhub.api.result.dto.response.AthleteResultHistoryResponse;
import pl.tfhub.api.result.exception.InvalidResultSearchException;
import pl.tfhub.api.result.service.ResultService;
import pl.tfhub.api.test.dto.response.TestResponse;

@RestController
@RequestMapping("/api/athletes")
public class AthleteController {

	private final AthleteService athleteService;
	private final ResultService resultService;

	public AthleteController(AthleteService athleteService, ResultService resultService) {
		this.athleteService = athleteService;
		this.resultService = resultService;
	}

	@PostMapping
	public ResponseEntity<AthleteResponse> createAthlete(@Valid @RequestBody CreateAthleteRequest request) {
		AthleteResponse athlete = athleteService.createAthlete(request);
		return ResponseEntity.created(URI.create("/api/athletes/" + athlete.id())).body(athlete);
	}

	@PostMapping("/search")
	public List<AthleteResponse> searchAthletes(@Valid @RequestBody SearchAthletesRequest request) {
		return athleteService.searchAthletes(request);
	}

	@GetMapping("/{id}")
	public AthleteResponse getAthlete(@PathVariable Long id) {
		if (id <= 0) {
			throw new InvalidAthleteIdException();
		}
		return athleteService.getAthlete(id);
	}

	@GetMapping("/{id}/tests")
	public List<TestResponse> getAthleteTests(@PathVariable Long id) {
		validateAthleteId(id);
		return resultService.getTestsWithResultsForAthlete(id);
	}

	@GetMapping("/{id}/results")
	public List<AthleteResultHistoryResponse> getAthleteResults(
			@PathVariable Long id,
			@RequestParam(required = false) List<String> testIds,
			@RequestParam(required = false) String fromDate,
			@RequestParam(required = false) String toDate) {
		validateAthleteId(id);
		return resultService.getAthleteResultHistory(
				id,
				parseTestIds(testIds),
				parseDate(fromDate),
				parseDate(toDate));
	}

	@PutMapping("/{id}")
	public AthleteResponse updateAthlete(
			@PathVariable Long id,
			@Valid @RequestBody UpdateAthleteRequest request) {
		if (id <= 0) {
			throw new InvalidAthleteIdException();
		}
		return athleteService.updateAthlete(id, request);
	}

	@PatchMapping("/{id}/status")
	public AthleteResponse changeAthleteStatus(
			@PathVariable Long id,
			@Valid @RequestBody ChangeAthleteStatusRequest request) {
		if (id <= 0) {
			throw new InvalidAthleteIdException();
		}
		return athleteService.changeAthleteStatus(id, request);
	}

	private void validateAthleteId(Long id) {
		if (id <= 0) {
			throw new InvalidAthleteIdException();
		}
	}

	private List<Long> parseTestIds(List<String> values) {
		if (values == null) {
			return null;
		}
		try {
			List<Long> testIds = values.stream().map(Long::parseLong).toList();
			if (testIds.stream().anyMatch(testId -> testId <= 0)) {
				throw new InvalidResultSearchException();
			}
			return testIds;
		} catch (NumberFormatException exception) {
			throw new InvalidResultSearchException();
		}
	}

	private LocalDate parseDate(String value) {
		if (value == null) {
			return null;
		}
		try {
			return LocalDate.parse(value);
		} catch (DateTimeParseException exception) {
			throw new InvalidResultSearchException();
		}
	}
}

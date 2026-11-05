package pl.tfhub.api.athlete;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.tfhub.api.athlete.dto.AthleteRequest;
import pl.tfhub.api.athlete.dto.AthleteResponse;

import java.net.URI;

@RestController
@RequestMapping("/api/athletes")
public class AthleteController {

	private final AthleteService athleteService;

	public AthleteController(AthleteService athleteService) {
		this.athleteService = athleteService;
	}

	@PostMapping
	public ResponseEntity<AthleteResponse> createAthlete(@Valid @RequestBody AthleteRequest request) {
		AthleteResponse athlete = athleteService.createAthlete(request);
		return ResponseEntity.created(URI.create("/api/athletes/" + athlete.id())).body(athlete);
	}

	@GetMapping("/{id}")
	public AthleteResponse getAthlete(@PathVariable Long id) {
		if (id <= 0) {
			throw new InvalidAthleteIdException();
		}
		return athleteService.getAthlete(id);
	}
}

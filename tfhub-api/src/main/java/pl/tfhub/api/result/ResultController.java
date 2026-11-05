package pl.tfhub.api.result;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.tfhub.api.result.dto.CreateResultRequest;
import pl.tfhub.api.result.dto.CreateMultipleResultsRequest;
import pl.tfhub.api.result.dto.CreateMultipleResultsResponse;
import pl.tfhub.api.result.dto.ResultResponse;

import java.net.URI;

@RestController
@RequestMapping("/api/results")
public class ResultController {

	private final ResultService resultService;

	public ResultController(ResultService resultService) {
		this.resultService = resultService;
	}

	@PostMapping
	public ResponseEntity<ResultResponse> createResult(@Valid @RequestBody CreateResultRequest request) {
		ResultResponse result = resultService.createResult(request);
		return ResponseEntity.created(URI.create("/api/results/" + result.id())).body(result);
	}

	@PostMapping("/batch")
	public ResponseEntity<CreateMultipleResultsResponse> createMultipleResults(
			@Valid @RequestBody CreateMultipleResultsRequest request) {
		return ResponseEntity.status(201).body(resultService.createMultipleResults(request));
	}
}

package pl.tfhub.api.result.controller;

import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.tfhub.api.result.dto.request.CreateMultipleResultsRequest;
import pl.tfhub.api.result.dto.request.CreateResultRequest;
import pl.tfhub.api.result.dto.response.CreateMultipleResultsResponse;
import pl.tfhub.api.result.dto.response.ResultResponse;
import pl.tfhub.api.result.exception.InvalidResultIdException;
import pl.tfhub.api.result.service.ResultService;

@RestController
@RequestMapping("/api/results")
public class ResultController {

	private final ResultService resultService;

	public ResultController(ResultService resultService) {
		this.resultService = resultService;
	}

	@GetMapping("/{id}")
	public ResultResponse getResult(@PathVariable String id) {
		long resultId;
		try {
			resultId = Long.parseLong(id);
		} catch (NumberFormatException exception) {
			throw new InvalidResultIdException();
		}
		if (resultId <= 0) {
			throw new InvalidResultIdException();
		}
		return resultService.getResult(resultId);
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

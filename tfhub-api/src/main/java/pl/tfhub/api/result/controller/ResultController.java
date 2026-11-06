package pl.tfhub.api.result.controller;

import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.tfhub.api.result.dto.request.CreateMultipleResultsRequest;
import pl.tfhub.api.result.dto.request.CreateResultRequest;
import pl.tfhub.api.result.dto.request.UpdateResultRequest;
import pl.tfhub.api.result.dto.response.CreateMultipleResultsResponse;
import pl.tfhub.api.result.dto.response.ResultResponse;
import pl.tfhub.api.result.enums.ResultType;
import pl.tfhub.api.result.exception.InvalidResultIdException;
import pl.tfhub.api.result.exception.InvalidResultSearchException;
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
		return resultService.getResult(parseResultId(id));
	}

	@GetMapping
	public List<ResultResponse> getResults(
			@RequestParam(required = false) String athleteId,
			@RequestParam(required = false) String testId,
			@RequestParam(required = false) String fromDate,
			@RequestParam(required = false) String toDate,
			@RequestParam(required = false) String type) {
		return resultService.searchResults(
				parseId(athleteId),
				parseId(testId),
				parseDate(fromDate),
				parseDate(toDate),
				parseType(type));
	}

	@PostMapping
	public ResponseEntity<ResultResponse> createResult(@Valid @RequestBody CreateResultRequest request) {
		ResultResponse result = resultService.createResult(request);
		return ResponseEntity.created(URI.create("/api/results/" + result.id())).body(result);
	}

	@PutMapping("/{id}")
	public ResultResponse updateResult(
			@PathVariable String id,
			@Valid @RequestBody UpdateResultRequest request) {
		return resultService.updateResult(parseResultId(id), request);
	}

	@PostMapping("/batch")
	public ResponseEntity<CreateMultipleResultsResponse> createMultipleResults(
			@Valid @RequestBody CreateMultipleResultsRequest request) {
		return ResponseEntity.status(201).body(resultService.createMultipleResults(request));
	}

	private Long parseId(String value) {
		if (value == null) {
			return null;
		}
		try {
			long id = Long.parseLong(value);
			if (id > 0) {
				return id;
			}
		} catch (NumberFormatException exception) {
			throw new InvalidResultSearchException();
		}
		throw new InvalidResultSearchException();
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

	private ResultType parseType(String value) {
		if (value == null) {
			return null;
		}
		try {
			return ResultType.valueOf(value);
		} catch (IllegalArgumentException exception) {
			throw new InvalidResultSearchException();
		}
	}

	private long parseResultId(String value) {
		try {
			long id = Long.parseLong(value);
			if (id > 0) {
				return id;
			}
		} catch (NumberFormatException exception) {
			throw new InvalidResultIdException();
		}
		throw new InvalidResultIdException();
	}
}

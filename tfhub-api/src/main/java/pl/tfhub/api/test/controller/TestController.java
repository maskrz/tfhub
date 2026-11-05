package pl.tfhub.api.test.controller;

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
import pl.tfhub.api.test.dto.request.ChangeTestStatusRequest;
import pl.tfhub.api.test.dto.request.CreateTestRequest;
import pl.tfhub.api.test.dto.request.TestSearchRequest;
import pl.tfhub.api.test.dto.request.UpdateTestRequest;
import pl.tfhub.api.test.dto.response.TestResponse;
import pl.tfhub.api.test.exception.InvalidTestIdException;
import pl.tfhub.api.test.service.TestService;

@RestController
@RequestMapping("/api/tests")
public class TestController {

	private final TestService testService;

	public TestController(TestService testService) {
		this.testService = testService;
	}

	@PostMapping
	public ResponseEntity<TestResponse> createTest(@Valid @RequestBody CreateTestRequest request) {
		TestResponse test = testService.createTest(request);
		return ResponseEntity.created(URI.create("/api/tests/" + test.id())).body(test);
	}

	@GetMapping("/{id}")
	public TestResponse getTest(@PathVariable Long id) {
		if (id <= 0) {
			throw new InvalidTestIdException();
		}
		return testService.getTest(id);
	}

	@PutMapping("/{id}")
	public TestResponse updateTest(
			@PathVariable Long id,
			@Valid @RequestBody UpdateTestRequest request) {
		if (id <= 0) {
			throw new InvalidTestIdException();
		}
		return testService.updateTest(id, request);
	}

	@PatchMapping("/{id}/status")
	public TestResponse changeTestStatus(
			@PathVariable Long id,
			@Valid @RequestBody ChangeTestStatusRequest request) {
		if (id <= 0) {
			throw new InvalidTestIdException();
		}
		return testService.changeTestStatus(id, request);
	}

	@PostMapping("/search")
	public List<TestResponse> searchTests(@Valid @RequestBody TestSearchRequest request) {
		return testService.searchTests(request);
	}
}

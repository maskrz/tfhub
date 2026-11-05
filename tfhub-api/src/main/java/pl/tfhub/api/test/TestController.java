package pl.tfhub.api.test;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.tfhub.api.test.dto.CreateTestRequest;
import pl.tfhub.api.test.dto.TestResponse;

import java.net.URI;

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
}

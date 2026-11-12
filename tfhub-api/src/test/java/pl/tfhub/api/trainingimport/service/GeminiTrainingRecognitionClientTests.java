package pl.tfhub.api.trainingimport.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.ObjectMapper;
import pl.tfhub.api.trainingimport.config.TrainingImportProperties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GeminiTrainingRecognitionClientTests {

	private final Logger logger = (Logger) LoggerFactory.getLogger(GeminiTrainingRecognitionClient.class);
	private ListAppender<ILoggingEvent> logs;

	@BeforeEach
	void captureLogs() {
		logs = new ListAppender<>();
		logs.start();
		logger.addAppender(logs);
	}

	@AfterEach
	void stopCapturingLogs() {
		logger.detachAppender(logs);
		logs.stop();
	}

	@Test
	void sendsOneAuthenticatedRequestAndRetainsProviderResponse() throws Exception {
		HttpClient httpClient = mock(HttpClient.class);
		HttpResponse<String> providerResponse = mock(HttpResponse.class);
		String rawResponse = """
				{"candidates":[{"content":{"parts":[{"text":"{\\"name\\":\\"session\\"}"}]}}]}
				""";
		when(providerResponse.statusCode()).thenReturn(200);
		when(providerResponse.body()).thenReturn(rawResponse);
		when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
				.thenReturn(providerResponse);
		TrainingImportProperties properties = configuredProperties();
		GeminiTrainingRecognitionClient client = new GeminiTrainingRecognitionClient(
				new ObjectMapper(), httpClient, properties);

		var result = client.recognize(new byte[] {(byte) 0xff, (byte) 0xd8});

		AtomicReference<HttpRequest> captured = new AtomicReference<>();
		verify(httpClient, times(1)).send(
				org.mockito.ArgumentMatchers.argThat(request -> {
					captured.set(request);
					return true;
				}),
				any(HttpResponse.BodyHandler.class));
		assertEquals(rawResponse, result.rawResponse());
		assertEquals("{\"name\":\"session\"}", result.candidateText());
		assertEquals("server-side-test-key", captured.get().headers().firstValue("x-goog-api-key").orElseThrow());
		assertFalse(captured.get().uri().toString().contains("server-side-test-key"));
		assertTrue(logs.list.isEmpty());
	}

	@Test
	void optInLogsFullRequestAndResponseBodiesWithoutCredentials() throws Exception {
		HttpClient httpClient = mock(HttpClient.class);
		HttpResponse<String> response = mock(HttpResponse.class);
		String rawResponse = "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"result\"}]}}]}";
		when(response.statusCode()).thenReturn(200);
		when(response.body()).thenReturn(rawResponse);
		when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
		TrainingImportProperties properties = configuredProperties();
		properties.setGeminiDiagnosticLoggingEnabled(true);
		ObjectMapper mapper = new ObjectMapper();
		GeminiTrainingRecognitionClient client = new GeminiTrainingRecognitionClient(mapper, httpClient, properties);

		client.recognize(new byte[] {(byte) 0xff, (byte) 0xd8});

		assertEquals(2, logs.list.size());
		String requestLog = logs.list.get(0).getFormattedMessage();
		String responseLog = logs.list.get(1).getFormattedMessage();
		assertTrue(requestLog.contains("request method=POST url=https://generativelanguage.googleapis.com/"));
		String body = requestLog.substring(requestLog.indexOf(" body=") + 6);
		var json = mapper.readTree(body);
		String prompt;
		try (var input = getClass().getClassLoader().getResourceAsStream("prompts/training-import-recognition.txt")) {
			prompt = new String(input.readAllBytes(), StandardCharsets.UTF_8);
		}
		assertEquals(prompt, json.path("contents").path(0).path("parts").path(0).path("text").textValue());
		assertEquals("/9g=", json.path("contents").path(0).path("parts").path(1)
				.path("inline_data").path("data").textValue());
		assertEquals("image/jpeg", json.path("contents").path(0).path("parts").path(1)
				.path("inline_data").path("mime_type").textValue());
		assertEquals(8192, json.path("generationConfig").path("maxOutputTokens").asInt());
		assertTrue(responseLog.contains("response status=200 body=" + rawResponse));
		assertEquals(requestLog.substring(21, 57), responseLog.substring(21, 57));
		assertFalse(requestLog.contains("server-side-test-key"));
		assertFalse(responseLog.contains("server-side-test-key"));
	}

	@Test
	void optInLogsProviderErrorBodiesAndRedactsTheConfiguredApiKey() throws Exception {
		HttpClient httpClient = mock(HttpClient.class);
		HttpResponse<String> response = mock(HttpResponse.class);
		when(response.statusCode()).thenReturn(429);
		when(response.body()).thenReturn("{\"error\":\"server-side-test-key\"}");
		when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
		TrainingImportProperties properties = configuredProperties();
		properties.setGeminiDiagnosticLoggingEnabled(true);
		GeminiTrainingRecognitionClient client = new GeminiTrainingRecognitionClient(
				new ObjectMapper(), httpClient, properties);

		assertThrows(TrainingImportProcessingException.class,
				() -> client.recognize(new byte[] {(byte) 0xff, (byte) 0xd8}));

		assertEquals(2, logs.list.size());
		String responseLog = logs.list.get(1).getFormattedMessage();
		assertTrue(responseLog.contains("response status=429 body={\"error\":\"[REDACTED]\"}"));
		assertFalse(responseLog.contains("server-side-test-key"));
	}

	@Test
	void providerErrorsAreMappedWithoutRetainingOrExposingProviderBody() throws Exception {
		HttpClient httpClient = mock(HttpClient.class);
		HttpResponse<String> providerResponse = mock(HttpResponse.class);
		when(providerResponse.statusCode()).thenReturn(429);
		when(providerResponse.body()).thenReturn("{\"error\":\"sensitive provider details\"}");
		when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
				.thenReturn(providerResponse);
		GeminiTrainingRecognitionClient client = new GeminiTrainingRecognitionClient(
				new ObjectMapper(), httpClient, configuredProperties());

		TrainingImportProcessingException exception = assertThrows(
				TrainingImportProcessingException.class,
				() -> client.recognize(new byte[] {(byte) 0xff, (byte) 0xd8}));

		assertEquals("GEMINI_RATE_LIMITED", exception.getFailureCode());
		assertFalse(exception.getSafeMessage().contains("sensitive provider details"));
		verify(httpClient, times(1)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
	}

	private TrainingImportProperties configuredProperties() {
		TrainingImportProperties properties = new TrainingImportProperties();
		properties.setGeminiApiKey("server-side-test-key");
		properties.setGeminiModel("gemini-test-model");
		return properties;
	}
}

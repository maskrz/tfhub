package pl.tfhub.api.trainingimport.service;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import pl.tfhub.api.trainingimport.config.TrainingImportProperties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GeminiTrainingRecognitionClientTests {

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

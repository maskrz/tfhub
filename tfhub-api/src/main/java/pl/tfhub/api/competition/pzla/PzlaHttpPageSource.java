package pl.tfhub.api.competition.pzla;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertPathBuilderException;
import java.security.cert.CertPathValidatorException;
import java.security.cert.CertificateException;
import java.time.Duration;
import javax.net.ssl.SSLHandshakeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import pl.tfhub.api.competition.config.PzlaProperties;
import pl.tfhub.api.competition.enums.CompetitionSeasonType;

@Component
public class PzlaHttpPageSource implements PzlaPageSource {

	private static final String BASE_URL = "https://statystyka.pzla.pl/personal.php";
	private static final Logger LOGGER = LoggerFactory.getLogger(PzlaHttpPageSource.class);

	private final HttpClient httpClient;
	private final PzlaRequestPacer requestPacer;
	private final PzlaProperties properties;

	public PzlaHttpPageSource(
			@Qualifier("pzlaHttpClient") HttpClient httpClient,
			PzlaRequestPacer requestPacer,
			PzlaProperties properties) {
		this.httpClient = httpClient;
		this.requestPacer = requestPacer;
		this.properties = properties;
	}

	@Override
	public String fetchCurrentSeason(String pzlaId) {
		validatePzlaId(pzlaId);
		return fetch("?page=sb&nr_zaw=" + encode(pzlaId) + "&r=2");
	}

	@Override
	public String fetchSeason(String pzlaId, PzlaSeason season) {
		validatePzlaId(pzlaId);
		if (season == null || season.year() < 1900 || season.year() > 9999
				|| (season.type() != CompetitionSeasonType.INDOOR
						&& season.type() != CompetitionSeasonType.OUTDOOR)) {
			throw new IllegalArgumentException("A valid PZLA season is required");
		}
		String seasonType = season.type() == CompetitionSeasonType.OUTDOOR ? "L" : "Z";
		return fetch("?page=sb&nr_zaw=" + encode(pzlaId)
				+ "&r=2&sezon=" + season.year() + "&sezon2=" + seasonType);
	}

	private String fetch(String query) {
		URI uri = URI.create(BASE_URL + query);
		for (int attempt = 0; attempt <= properties.getRetryCount(); attempt++) {
			requestPacer.waitBeforeNextRequest();
			try {
				HttpRequest request = HttpRequest.newBuilder(uri)
						.timeout(Duration.ofMillis(properties.getRequestTimeoutMs()))
						.header("Accept", "text/html")
						.header("User-Agent", "TFHub PZLA results synchronization")
						.GET()
						.build();
				HttpResponse<String> response = httpClient.send(
						request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
				if (response.statusCode() == 200) {
					return response.body();
				}
				LOGGER.warn("PZLA request attempt {} of {} returned HTTP {}",
						attempt + 1, properties.getRetryCount() + 1, response.statusCode());
				if (!isRetryableStatus(response.statusCode()) || attempt == properties.getRetryCount()) {
					throw new PzlaSynchronizationException(
							"PZLA_HTTP_" + response.statusCode(),
							"PZLA returned HTTP " + response.statusCode());
				}
			} catch (IOException exception) {
				if (hasCause(exception, SSLHandshakeException.class)) {
					boolean certificateFailure = hasCause(exception, CertificateException.class)
							|| hasCause(exception, CertPathBuilderException.class)
							|| hasCause(exception, CertPathValidatorException.class);
					String code = certificateFailure ? "PZLA_TLS_CERTIFICATE_INVALID" : "PZLA_TLS_HANDSHAKE_FAILED";
					String message = certificateFailure
							? "Nie udało się zweryfikować certyfikatu TLS PZLA. Sprawdź łańcuch certyfikatów i magazyn zaufanych certyfikatów Java."
							: "Nie udało się nawiązać bezpiecznego połączenia TLS z PZLA.";
					LOGGER.warn("PZLA request attempt {} failed: {}; no retry", attempt + 1, code);
					throw new PzlaSynchronizationException(code, message, exception);
				}
				LOGGER.warn("PZLA request attempt {} of {} failed: {}",
						attempt + 1, properties.getRetryCount() + 1, exception.getClass().getSimpleName());
				if (attempt == properties.getRetryCount()) {
					throw new PzlaSynchronizationException(
							"PZLA_CONNECTION_FAILED", "Could not retrieve results from PZLA", exception);
				}
			} catch (InterruptedException exception) {
				Thread.currentThread().interrupt();
				LOGGER.warn("PZLA request attempt {} interrupted", attempt + 1);
				throw new PzlaSynchronizationException(
						"PZLA_SYNC_INTERRUPTED", "PZLA synchronization was interrupted", exception);
			} finally {
				requestPacer.markRequestCompleted();
			}
		}
		throw new PzlaSynchronizationException(
				"PZLA_CONNECTION_FAILED", "Could not retrieve results from PZLA");
	}

	private boolean isRetryableStatus(int statusCode) {
		return statusCode == 429 || statusCode == 502 || statusCode == 503 || statusCode == 504;
	}

	private boolean hasCause(Throwable exception, Class<? extends Throwable> type) {
		for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
			if (type.isInstance(cause)) {
				return true;
			}
		}
		return false;
	}

	private String encode(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8);
	}

	private void validatePzlaId(String pzlaId) {
		if (pzlaId == null || !pzlaId.matches("[0-9]{1,20}")) {
			throw new IllegalArgumentException("PZLA ID must contain only digits");
		}
	}
}

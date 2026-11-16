package pl.tfhub.api.competition.pzla;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.cert.CertPathBuilderException;
import java.security.cert.CertificateException;
import javax.net.ssl.SSLHandshakeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.tfhub.api.competition.config.PzlaProperties;

class PzlaHttpPageSourceTests {

	private final HttpClient client = mock(HttpClient.class);
	private final PzlaRequestPacer pacer = mock(PzlaRequestPacer.class);
	private PzlaHttpPageSource source;

	@BeforeEach
	void setUp() {
		source = new PzlaHttpPageSource(client, pacer, new PzlaProperties());
	}

	@Test
	void certificateValidationFailureIsReportedWithoutRetry() throws Exception {
		SSLHandshakeException handshake = new SSLHandshakeException("PKIX path building failed");
		handshake.initCause(new CertificateException("Certificate validation failed",
				new CertPathBuilderException("unable to find valid certification path")));
		when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenThrow(handshake);

		assertThatThrownBy(() -> source.fetchCurrentSeason("162803"))
				.isInstanceOfSatisfying(PzlaSynchronizationException.class, exception -> {
					assertThat(exception.getCode()).isEqualTo("PZLA_TLS_CERTIFICATE_INVALID");
					assertThat(exception.getMessage()).contains("certyfikatu TLS");
					assertThat(exception.getCause()).isSameAs(handshake);
				});
		verify(client).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
		verify(pacer).waitBeforeNextRequest();
		verify(pacer).markRequestCompleted();
	}

	@Test
	void otherTlsHandshakeFailuresAreReportedWithoutRetry() throws Exception {
		SSLHandshakeException handshake = new SSLHandshakeException("TLS handshake failure");
		when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenThrow(handshake);

		assertThatThrownBy(() -> source.fetchCurrentSeason("162803"))
				.isInstanceOfSatisfying(PzlaSynchronizationException.class, exception ->
						assertThat(exception.getCode()).isEqualTo("PZLA_TLS_HANDSHAKE_FAILED"));
		verify(client).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
		verify(pacer).markRequestCompleted();
	}

	@Test
	void wrappedCertificateHandshakeFailureIsNotRetried() throws Exception {
		SSLHandshakeException handshake = new SSLHandshakeException("handshake failure");
		handshake.initCause(new CertificateException("certificate failure"));
		when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
				.thenThrow(new IOException("connection failed", handshake));

		assertThatThrownBy(() -> source.fetchCurrentSeason("162803"))
				.isInstanceOfSatisfying(PzlaSynchronizationException.class, exception ->
						assertThat(exception.getCode()).isEqualTo("PZLA_TLS_CERTIFICATE_INVALID"));
		verify(client).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
		verify(pacer).markRequestCompleted();
	}

	@Test
	void temporaryIoFailuresStillRetryWithPacing() throws Exception {
		when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
				.thenThrow(new IOException("temporary"));

		assertThatThrownBy(() -> source.fetchCurrentSeason("162803"))
				.isInstanceOfSatisfying(PzlaSynchronizationException.class, exception ->
						assertThat(exception.getCode()).isEqualTo("PZLA_CONNECTION_FAILED"));
		verify(client, times(3)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
		verify(pacer, times(3)).waitBeforeNextRequest();
		verify(pacer, times(3)).markRequestCompleted();
	}
}

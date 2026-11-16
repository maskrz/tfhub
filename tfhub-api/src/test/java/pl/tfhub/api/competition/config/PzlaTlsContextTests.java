package pl.tfhub.api.competition.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.HexFormat;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ClassPathResource;

class PzlaTlsContextTests {

	@Test
	void bundledIntermediateIsSignedByTheVerifiedRootAndDefaultTrustIsRetained() throws Exception {
		X509Certificate root = certificate("pzla/certum-trusted-root-ca.pem");
		X509Certificate intermediate = certificate("pzla/certum-dv-tls-g2-r39-ca.pem");
		assertThat(fingerprint(root)).isEqualTo("fe7696573855773e37a95e7ad4d9cc96c30157c15d31765ba9b15704e1ae78fd");
		assertThat(fingerprint(intermediate))
				.isEqualTo("83c0a5a76844c840dfaf820ffd02adf6573a26823ef6af758a3384a0ac044083");
		intermediate.verify(root.getPublicKey());
		assertThat(intermediate.getIssuerX500Principal()).isEqualTo(root.getSubjectX500Principal());
		assertThat(intermediate.getBasicConstraints()).isGreaterThanOrEqualTo(0);

		TrustManagerFactory defaults = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
		defaults.init((KeyStore) null);
		X509TrustManager defaultManager = x509Manager(defaults.getTrustManagers());
		X509TrustManager pzlaManager = x509Manager(PzlaTlsContext.trustManagers());
		assertThat(Arrays.asList(pzlaManager.getAcceptedIssuers()))
				.contains(root)
				.containsAll(Arrays.asList(defaultManager.getAcceptedIssuers()))
				.doesNotContain(intermediate);
	}

	@Test
	void untrustedSelfSignedServerCertificateIsRejected(@TempDir Path temp) throws Exception {
		Path store = temp.resolve("untrusted.p12");
		String password = "temporary-test-password";
		Path keytool = Path.of(System.getProperty("java.home"), "bin",
				System.getProperty("os.name").startsWith("Windows") ? "keytool.exe" : "keytool");
		Process process = new ProcessBuilder(keytool.toString(), "-genkeypair", "-alias", "untrusted",
				"-keyalg", "RSA", "-keysize", "2048", "-dname", "CN=localhost",
				"-validity", "1", "-storetype", "PKCS12", "-keystore", store.toString(),
				"-storepass", password, "-keypass", password, "-noprompt")
				.redirectErrorStream(true).start();
		String output = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
		assertThat(process.waitFor()).as(output).isZero();
		KeyStore keyStore = KeyStore.getInstance("PKCS12");
		try (InputStream stream = Files.newInputStream(store)) {
			keyStore.load(stream, password.toCharArray());
		}
		X509Certificate untrusted = (X509Certificate) keyStore.getCertificate("untrusted");
		X509TrustManager manager = x509Manager(PzlaTlsContext.trustManagers());
		assertThatThrownBy(() -> manager.checkServerTrusted(new X509Certificate[] {untrusted}, "RSA"))
				.isInstanceOf(CertificateException.class);
	}

	private X509TrustManager x509Manager(TrustManager[] managers) {
		return Arrays.stream(managers)
				.filter(X509TrustManager.class::isInstance)
				.map(X509TrustManager.class::cast)
				.findFirst()
				.orElseThrow();
	}

	private X509Certificate certificate(String path) throws Exception {
		try (InputStream stream = new ClassPathResource(path).getInputStream()) {
			return (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(stream);
		}
	}

	private String fingerprint(X509Certificate certificate) throws Exception {
		return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(certificate.getEncoded()));
	}
}

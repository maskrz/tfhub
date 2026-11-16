package pl.tfhub.api.competition.config;

import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.CertStore;
import java.security.cert.CertificateFactory;
import java.security.cert.CollectionCertStoreParameters;
import java.security.cert.PKIXBuilderParameters;
import java.security.cert.X509CertSelector;
import java.security.cert.X509Certificate;
import java.util.List;
import javax.net.ssl.CertPathTrustManagerParameters;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import org.springframework.core.io.ClassPathResource;

final class PzlaTlsContext {

	private PzlaTlsContext() {
	}

	static SSLContext create() throws GeneralSecurityException, IOException {
		SSLContext context = SSLContext.getInstance("TLS");
		context.init(null, trustManagers(), null);
		return context;
	}

	static TrustManager[] trustManagers() throws GeneralSecurityException, IOException {
		TrustManagerFactory defaults = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
		defaults.init((KeyStore) null);
		KeyStore anchors = KeyStore.getInstance(KeyStore.getDefaultType());
		anchors.load(null, null);
		int index = 0;
		for (TrustManager manager : defaults.getTrustManagers()) {
			if (manager instanceof X509TrustManager trustManager) {
				for (X509Certificate certificate : trustManager.getAcceptedIssuers()) {
					anchors.setCertificateEntry("default-" + index++, certificate);
				}
			}
		}
		if (index == 0) {
			throw new GeneralSecurityException("No default TLS trust anchors are available");
		}

		X509Certificate root = certificate("pzla/certum-trusted-root-ca.pem");
		X509Certificate intermediate = certificate("pzla/certum-dv-tls-g2-r39-ca.pem");
		intermediate.verify(root.getPublicKey());
		anchors.setCertificateEntry("pzla-certum-root", root);

		PKIXBuilderParameters parameters = new PKIXBuilderParameters(anchors, new X509CertSelector());
		parameters.setRevocationEnabled(Boolean.getBoolean("com.sun.net.ssl.checkRevocation"));
		parameters.addCertStore(CertStore.getInstance("Collection",
				new CollectionCertStoreParameters(List.of(intermediate))));
		TrustManagerFactory factory = TrustManagerFactory.getInstance("PKIX");
		factory.init(new CertPathTrustManagerParameters(parameters));
		return factory.getTrustManagers();
	}

	private static X509Certificate certificate(String path) throws GeneralSecurityException, IOException {
		try (InputStream stream = new ClassPathResource(path).getInputStream()) {
			return (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(stream);
		}
	}
}

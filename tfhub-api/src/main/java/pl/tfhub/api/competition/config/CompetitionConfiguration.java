package pl.tfhub.api.competition.config;

import java.io.IOException;
import java.net.http.HttpClient;
import java.security.GeneralSecurityException;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import pl.tfhub.api.competition.pzla.PzlaRequestPacer;
import pl.tfhub.api.competition.pzla.PzlaPageParser;

@Configuration
@EnableConfigurationProperties(PzlaProperties.class)
public class CompetitionConfiguration {

	@Bean(name = "pzlaHttpClient")
	HttpClient pzlaHttpClient(PzlaProperties properties) throws GeneralSecurityException, IOException {
		return HttpClient.newBuilder()
				.connectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()))
				.sslContext(PzlaTlsContext.create())
				.followRedirects(HttpClient.Redirect.NORMAL)
				.build();
	}

	@Bean(name = "competitionSyncTaskExecutor")
	ThreadPoolTaskExecutor competitionSyncTaskExecutor(PzlaProperties properties) {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(1);
		executor.setMaxPoolSize(1);
		executor.setQueueCapacity(properties.getQueueCapacity());
		executor.setThreadNamePrefix("competition-sync-");
		executor.setWaitForTasksToCompleteOnShutdown(false);
		return executor;
	}

	@Bean
	PzlaRequestPacer pzlaRequestPacer(PzlaProperties properties) {
		return new PzlaRequestPacer(properties.getCooldownMs());
	}

	@Bean
	PzlaPageParser pzlaPageParser() {
		return new PzlaPageParser();
	}
}

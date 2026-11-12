package pl.tfhub.api.trainingimport.config;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(TrainingImportProperties.class)
public class TrainingImportConfiguration {

	@Bean(name = "geminiHttpClient")
	HttpClient geminiHttpClient(TrainingImportProperties properties) {
		return HttpClient.newBuilder()
				.connectTimeout(Duration.ofMillis(properties.getGeminiConnectTimeoutMs()))
				.build();
	}

	@Bean(name = "trainingImportTaskExecutor")
	ThreadPoolTaskExecutor trainingImportTaskExecutor(TrainingImportProperties properties) {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(1);
		executor.setMaxPoolSize(1);
		executor.setQueueCapacity(properties.getProcessingQueueCapacity());
		executor.setThreadNamePrefix("training-import-");
		executor.setWaitForTasksToCompleteOnShutdown(false);
		return executor;
	}
}

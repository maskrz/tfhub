package pl.tfhub.api.trainingimport.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(TrainingImportProperties.class)
public class TrainingImportConfiguration {
}

package pl.tfhub.api.trainingimport.config;

import java.nio.file.Path;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import org.springframework.util.unit.DataSize;

@ConfigurationProperties(prefix = "tfhub.training-import")
@Validated
public class TrainingImportProperties {

	@NotNull
	private Path storagePath = Path.of("./data/training-imports");

	@NotNull
	private DataSize maxUploadSize = DataSize.ofMegabytes(15);

	@Positive
	private int maxImageWidth = 8000;

	@Positive
	private int maxImageHeight = 8000;

	@Positive
	private long maxImagePixels = 24_000_000;

	public Path getStoragePath() {
		return storagePath;
	}

	public void setStoragePath(Path storagePath) {
		this.storagePath = storagePath;
	}

	public DataSize getMaxUploadSize() {
		return maxUploadSize;
	}

	public void setMaxUploadSize(DataSize maxUploadSize) {
		this.maxUploadSize = maxUploadSize;
	}

	public int getMaxImageWidth() {
		return maxImageWidth;
	}

	public void setMaxImageWidth(int maxImageWidth) {
		this.maxImageWidth = maxImageWidth;
	}

	public int getMaxImageHeight() {
		return maxImageHeight;
	}

	public void setMaxImageHeight(int maxImageHeight) {
		this.maxImageHeight = maxImageHeight;
	}

	public long getMaxImagePixels() {
		return maxImagePixels;
	}

	public void setMaxImagePixels(long maxImagePixels) {
		this.maxImagePixels = maxImagePixels;
	}
}

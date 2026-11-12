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

	private boolean processingEnabled = true;

	private String geminiApiKey = "";

	private String geminiModel = "";

	private boolean geminiDiagnosticLoggingEnabled = false;

	@Positive
	private int geminiMaxOutputTokens = 8_192;

	@Positive
	private long geminiConnectTimeoutMs = 5_000;

	@Positive
	private long processingRequestTimeoutMs = 90_000;

	@Positive
	private long processingStaleTimeoutMs = 1_200_000;

	@Positive
	private long processingPollIntervalMs = 5_000;

	@Positive
	private int processingQueueCapacity = 8;

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

	public boolean isProcessingEnabled() {
		return processingEnabled;
	}

	public void setProcessingEnabled(boolean processingEnabled) {
		this.processingEnabled = processingEnabled;
	}

	public String getGeminiApiKey() {
		return geminiApiKey;
	}

	public void setGeminiApiKey(String geminiApiKey) {
		this.geminiApiKey = geminiApiKey;
	}

	public String getGeminiModel() {
		return geminiModel;
	}

	public void setGeminiModel(String geminiModel) {
		this.geminiModel = geminiModel;
	}

	public boolean isGeminiDiagnosticLoggingEnabled() {
		return geminiDiagnosticLoggingEnabled;
	}

	public void setGeminiDiagnosticLoggingEnabled(boolean geminiDiagnosticLoggingEnabled) {
		this.geminiDiagnosticLoggingEnabled = geminiDiagnosticLoggingEnabled;
	}

	public int getGeminiMaxOutputTokens() {
		return geminiMaxOutputTokens;
	}

	public void setGeminiMaxOutputTokens(int geminiMaxOutputTokens) {
		this.geminiMaxOutputTokens = geminiMaxOutputTokens;
	}

	public long getGeminiConnectTimeoutMs() {
		return geminiConnectTimeoutMs;
	}

	public void setGeminiConnectTimeoutMs(long geminiConnectTimeoutMs) {
		this.geminiConnectTimeoutMs = geminiConnectTimeoutMs;
	}

	public long getProcessingRequestTimeoutMs() {
		return processingRequestTimeoutMs;
	}

	public void setProcessingRequestTimeoutMs(long processingRequestTimeoutMs) {
		this.processingRequestTimeoutMs = processingRequestTimeoutMs;
	}

	public long getProcessingStaleTimeoutMs() {
		return processingStaleTimeoutMs;
	}

	public void setProcessingStaleTimeoutMs(long processingStaleTimeoutMs) {
		this.processingStaleTimeoutMs = processingStaleTimeoutMs;
	}

	public long getProcessingPollIntervalMs() {
		return processingPollIntervalMs;
	}

	public void setProcessingPollIntervalMs(long processingPollIntervalMs) {
		this.processingPollIntervalMs = processingPollIntervalMs;
	}

	public int getProcessingQueueCapacity() {
		return processingQueueCapacity;
	}

	public void setProcessingQueueCapacity(int processingQueueCapacity) {
		this.processingQueueCapacity = processingQueueCapacity;
	}
}

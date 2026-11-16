package pl.tfhub.api.competition.config;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "tfhub.pzla")
@Validated
public class PzlaProperties {

	@Positive
	private long connectTimeoutMs = 5_000;

	@Positive
	private long requestTimeoutMs = 20_000;

	@Positive
	private long cooldownMs = 5_000;

	@Positive
	private int retryCount = 2;

	@Positive
	private int queueCapacity = 200;

	public long getConnectTimeoutMs() {
		return connectTimeoutMs;
	}

	public void setConnectTimeoutMs(long connectTimeoutMs) {
		this.connectTimeoutMs = connectTimeoutMs;
	}

	public long getRequestTimeoutMs() {
		return requestTimeoutMs;
	}

	public void setRequestTimeoutMs(long requestTimeoutMs) {
		this.requestTimeoutMs = requestTimeoutMs;
	}

	public long getCooldownMs() {
		return cooldownMs;
	}

	public void setCooldownMs(long cooldownMs) {
		this.cooldownMs = cooldownMs;
	}

	public int getRetryCount() {
		return retryCount;
	}

	public void setRetryCount(int retryCount) {
		this.retryCount = retryCount;
	}

	public int getQueueCapacity() {
		return queueCapacity;
	}

	public void setQueueCapacity(int queueCapacity) {
		this.queueCapacity = queueCapacity;
	}
}

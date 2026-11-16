package pl.tfhub.api.competition.pzla;

import java.util.concurrent.TimeUnit;

public class PzlaRequestPacer {

	private final long cooldownNanos;
	private long lastRequestCompletedAt;

	public PzlaRequestPacer(long cooldownMs) {
		this.cooldownNanos = TimeUnit.MILLISECONDS.toNanos(cooldownMs);
	}

	public synchronized void waitBeforeNextRequest() {
		if (lastRequestCompletedAt == 0) {
			return;
		}
		long waitNanos = cooldownNanos - (System.nanoTime() - lastRequestCompletedAt);
		if (waitNanos <= 0) {
			return;
		}
		try {
			TimeUnit.NANOSECONDS.sleep(waitNanos);
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new PzlaSynchronizationException(
					"PZLA_SYNC_INTERRUPTED", "PZLA synchronization was interrupted", exception);
		}
	}

	public synchronized void markRequestCompleted() {
		lastRequestCompletedAt = System.nanoTime();
	}
}

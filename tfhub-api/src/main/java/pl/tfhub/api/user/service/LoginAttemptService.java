package pl.tfhub.api.user.service;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Service;

@Service
public class LoginAttemptService {

	private static final int MAX_FAILED_ATTEMPTS = 5;
	private static final Duration WINDOW = Duration.ofMinutes(15);
	private static final Duration COOLDOWN = Duration.ofMinutes(15);

	private final ConcurrentMap<String, Attempts> attemptsByAddress = new ConcurrentHashMap<>();

	public boolean isBlocked(String address) {
		Attempts attempts = attemptsByAddress.get(address);
		if (attempts == null) {
			return false;
		}
		Instant now = Instant.now();
		if (attempts.blockedUntil() != null && now.isBefore(attempts.blockedUntil())) {
			return true;
		}
		if (attempts.blockedUntil() != null || now.isAfter(attempts.windowStarted().plus(WINDOW))) {
			attemptsByAddress.remove(address, attempts);
		}
		return false;
	}

	public void recordFailure(String address) {
		Instant now = Instant.now();
		attemptsByAddress.compute(address, (key, previous) -> {
			Attempts current = previous == null || now.isAfter(previous.windowStarted().plus(WINDOW))
					? new Attempts(now, 0, null)
					: previous;
			int failures = current.failures() + 1;
			return new Attempts(current.windowStarted(), failures,
					failures >= MAX_FAILED_ATTEMPTS ? now.plus(COOLDOWN) : null);
		});
	}

	public void recordSuccess(String address) {
		attemptsByAddress.remove(address);
	}

	private record Attempts(Instant windowStarted, int failures, Instant blockedUntil) {
	}
}

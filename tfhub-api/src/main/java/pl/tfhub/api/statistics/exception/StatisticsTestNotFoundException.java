package pl.tfhub.api.statistics.exception;

public class StatisticsTestNotFoundException extends RuntimeException {

	public StatisticsTestNotFoundException(long testId) {
		super("Test not found: " + testId);
	}
}

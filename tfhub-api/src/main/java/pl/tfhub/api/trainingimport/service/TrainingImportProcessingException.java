package pl.tfhub.api.trainingimport.service;

public class TrainingImportProcessingException extends RuntimeException {

	private final String failureCode;
	private final String safeMessage;
	private final boolean retryable;

	public TrainingImportProcessingException(String failureCode, String safeMessage) {
		this(failureCode, safeMessage, false);
	}

	public TrainingImportProcessingException(
			String failureCode,
			String safeMessage,
			boolean retryable) {
		super(safeMessage);
		this.failureCode = failureCode;
		this.safeMessage = safeMessage;
		this.retryable = retryable;
	}

	public String getFailureCode() {
		return failureCode;
	}

	public String getSafeMessage() {
		return safeMessage;
	}

	public boolean isRetryable() {
		return retryable;
	}
}

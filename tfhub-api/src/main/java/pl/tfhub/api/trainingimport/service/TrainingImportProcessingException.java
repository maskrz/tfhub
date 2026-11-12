package pl.tfhub.api.trainingimport.service;

public class TrainingImportProcessingException extends RuntimeException {

	private final String failureCode;
	private final String safeMessage;

	public TrainingImportProcessingException(String failureCode, String safeMessage) {
		super(safeMessage);
		this.failureCode = failureCode;
		this.safeMessage = safeMessage;
	}

	public String getFailureCode() {
		return failureCode;
	}

	public String getSafeMessage() {
		return safeMessage;
	}
}

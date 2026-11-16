package pl.tfhub.api.competition.pzla;

public class PzlaSynchronizationException extends RuntimeException {

	private final String code;

	public PzlaSynchronizationException(String code, String safeMessage) {
		super(safeMessage);
		this.code = code;
	}

	public PzlaSynchronizationException(String code, String safeMessage, Throwable cause) {
		super(safeMessage, cause);
		this.code = code;
	}

	public String getCode() {
		return code;
	}
}

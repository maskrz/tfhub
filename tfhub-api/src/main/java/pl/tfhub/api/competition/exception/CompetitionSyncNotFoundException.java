package pl.tfhub.api.competition.exception;

public class CompetitionSyncNotFoundException extends RuntimeException {

	public CompetitionSyncNotFoundException(long id) {
		super("Competition synchronization run not found: " + id);
	}
}

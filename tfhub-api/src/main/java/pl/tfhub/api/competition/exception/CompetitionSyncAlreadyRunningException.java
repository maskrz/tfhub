package pl.tfhub.api.competition.exception;

public class CompetitionSyncAlreadyRunningException extends RuntimeException {

	public CompetitionSyncAlreadyRunningException() {
		super("A competition-results synchronization is already queued or running for this athlete");
	}
}

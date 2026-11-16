package pl.tfhub.api.competition.exception;

public class PzlaIdRequiredException extends RuntimeException {

	public PzlaIdRequiredException() {
		super("A PZLA athlete ID must be configured before synchronization");
	}
}

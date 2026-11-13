package pl.tfhub.api.athlete.exception;

public class AthleteAliasConflictException extends RuntimeException {
	public AthleteAliasConflictException() {
		super("An athlete alias conflicts with another alias in the group");
	}
}

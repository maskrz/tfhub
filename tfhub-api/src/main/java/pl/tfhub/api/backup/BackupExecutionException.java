package pl.tfhub.api.backup;

public class BackupExecutionException extends RuntimeException {

	public BackupExecutionException(Throwable cause) {
		super("Backup execution failed", cause);
	}
}

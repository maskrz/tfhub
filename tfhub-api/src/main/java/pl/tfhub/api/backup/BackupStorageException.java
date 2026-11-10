package pl.tfhub.api.backup;

public class BackupStorageException extends RuntimeException {

	public BackupStorageException(String message) {
		super(message);
	}

	public BackupStorageException(String message, Throwable cause) {
		super(message, cause);
	}
}

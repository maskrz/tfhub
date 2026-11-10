package pl.tfhub.api.backup;

import java.nio.file.Path;
import java.util.List;

public interface BackupStorage {

	void uploadAndVerify(Path localFile, String fileName);

	List<String> listBackupFiles();

	void deleteBackupFile(String fileName);
}

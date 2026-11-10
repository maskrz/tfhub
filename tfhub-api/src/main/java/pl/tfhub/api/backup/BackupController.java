package pl.tfhub.api.backup;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/backup")
public class BackupController {

	private final BackupService backupService;

	public BackupController(BackupService backupService) {
		this.backupService = backupService;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.OK)
	public BackupResponse createBackup() {
		return backupService.execute(BackupExecutionSource.MANUAL);
	}
}

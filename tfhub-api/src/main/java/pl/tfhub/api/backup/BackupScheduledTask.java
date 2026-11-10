package pl.tfhub.api.backup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class BackupScheduledTask {

	private static final Logger LOGGER = LoggerFactory.getLogger(BackupScheduledTask.class);
	private final BackupService backupService;

	public BackupScheduledTask(BackupService backupService) {
		this.backupService = backupService;
	}

	@Scheduled(cron = "${backup.schedule.cron:0 0 3 * * SUN}", zone = "Europe/Warsaw")
	public void runWeeklyBackup() {
		try {
			backupService.execute(BackupExecutionSource.SCHEDULED);
		} catch (BackupAlreadyRunningException exception) {
			LOGGER.info("Scheduled backup skipped because another backup is running");
		} catch (BackupExecutionException exception) {
			LOGGER.error("Scheduled backup failed", exception);
		}
	}
}

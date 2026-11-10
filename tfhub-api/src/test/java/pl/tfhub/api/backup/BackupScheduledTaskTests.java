package pl.tfhub.api.backup;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class BackupScheduledTaskTests {

	@Test
	void delegatesScheduledExecutionToTheSharedBackupService() throws Exception {
		BackupService service = mock(BackupService.class);
		BackupScheduledTask task = new BackupScheduledTask(service);

		task.runWeeklyBackup();

		verify(service).execute(BackupExecutionSource.SCHEDULED);
		Scheduled scheduled = BackupScheduledTask.class
				.getMethod("runWeeklyBackup")
				.getAnnotation(Scheduled.class);
		org.junit.jupiter.api.Assertions.assertEquals("Europe/Warsaw", scheduled.zone());
		org.junit.jupiter.api.Assertions.assertTrue(scheduled.cron().contains("0 0 3 * * SUN"));
	}
}

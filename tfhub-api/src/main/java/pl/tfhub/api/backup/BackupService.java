package pl.tfhub.api.backup;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class BackupService {

	private static final Logger LOGGER = LoggerFactory.getLogger(BackupService.class);
	private static final ZoneId WARSAW = ZoneId.of("Europe/Warsaw");
	private static final DateTimeFormatter FILE_TIMESTAMP = DateTimeFormatter.ofPattern("uuuu-MM-dd_HH-mm-ss");
	private final BackupProperties properties;
	private final BackupExecutionLock executionLock;
	private final BackupExporter exporter;
	private final BackupStorage storage;

	public BackupService(
			BackupProperties properties,
			BackupExecutionLock executionLock,
			BackupExporter exporter,
			BackupStorage storage) {
		this.properties = properties;
		this.executionLock = executionLock;
		this.exporter = exporter;
		this.storage = storage;
	}

	public BackupResponse execute(BackupExecutionSource source) {
		if (!executionLock.tryAcquire()) {
			throw new BackupAlreadyRunningException();
		}

		OffsetDateTime startedAt = OffsetDateTime.now(WARSAW);
		String fileName = null;
		Path temporaryDirectory = null;
		String warning = null;
		String cleanupWarning = null;
		boolean succeeded = false;
		BackupResponse response = null;
		BackupExecutionException failure = null;
		LOGGER.info("Backup started: source={}, startedAt={}", source, startedAt);
		try {
			temporaryDirectory = createTemporaryDirectory();
			OffsetDateTime exportedAt = OffsetDateTime.now(WARSAW).withNano(0);
			fileName = "tfhub_backup_" + FILE_TIMESTAMP.format(exportedAt) + ".xlsx";
			Path workbook = exporter.export(temporaryDirectory, fileName, exportedAt);

			if (storage.listBackupFiles().contains(fileName)) {
				throw new BackupStorageException("A backup with the generated file name already exists");
			}
			storage.uploadAndVerify(workbook, fileName);
			warning = applyRetention(fileName);
			succeeded = true;
			response = new BackupResponse(fileName, exportedAt, warning);
		} catch (Exception exception) {
			LOGGER.error("Backup failed: source={}, fileName={}, startedAt={}", source, fileName, startedAt, exception);
			failure = new BackupExecutionException(exception);
		} finally {
			cleanupWarning = cleanup(temporaryDirectory);
			String finalWarning = combineWarnings(warning, cleanupWarning);
			OffsetDateTime finishedAt = OffsetDateTime.now(WARSAW);
			if (cleanupWarning != null) {
				LOGGER.error("Backup temporary-file cleanup failed: source={}, fileName={}", source, fileName);
			}
			LOGGER.info("Backup finished: source={}, status={}, fileName={}, startedAt={}, finishedAt={}, warning={}",
					source, succeeded ? "SUCCESS" : "FAILURE", fileName, startedAt, finishedAt, finalWarning);
			executionLock.release();
		}
		if (failure != null) {
			throw failure;
		}
		if (response == null) {
			throw new IllegalStateException("Backup finished without a result");
		}
		return new BackupResponse(response.fileName(), response.executedAt(),
				combineWarnings(response.warning(), cleanupWarning));
	}

	private Path createTemporaryDirectory() throws IOException {
		Path baseDirectory = properties.getTemporaryDirectory();
		Files.createDirectories(baseDirectory);
		return Files.createTempDirectory(baseDirectory, "tfhub-backup-");
	}

	private String applyRetention(String newlyUploadedFile) {
		List<String> backups;
		try {
			backups = storage.listBackupFiles();
		} catch (Exception exception) {
			LOGGER.warn("Backup retention could not list remote files: {}", exception.getMessage());
			return "Backup uploaded and verified, but retention could not be applied.";
		}
		if (!backups.contains(newlyUploadedFile)) {
			LOGGER.warn("Backup retention skipped because the verified upload was not listed: {}", newlyUploadedFile);
			return "Backup uploaded and verified, but retention could not confirm the remote listing.";
		}

		List<String> newestFirst = backups.stream()
				.filter(BackupFileNames::isValid)
				.distinct()
				.sorted(Comparator.reverseOrder())
				.toList();
		List<String> expired = newestFirst.stream().skip(properties.getRetentionCount()).toList();
		List<String> failures = new ArrayList<>();
		for (String oldFile : expired) {
			try {
				storage.deleteBackupFile(oldFile);
			} catch (Exception exception) {
				failures.add(oldFile);
				LOGGER.warn("Unable to delete expired backup {}: {}", oldFile, exception.getMessage());
			}
		}
		return failures.isEmpty()
				? null
				: "Backup uploaded and verified, but one or more old backups could not be deleted.";
	}

	private String cleanup(Path directory) {
		if (directory == null || !Files.exists(directory)) {
			return null;
		}
		try (Stream<Path> paths = Files.walk(directory)) {
			for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
				Files.delete(path);
			}
			return null;
		} catch (IOException exception) {
			LOGGER.error("Unable to remove temporary backup files from {}", directory, exception);
			return "Temporary backup files could not all be removed.";
		}
	}

	private String combineWarnings(String first, String second) {
		if (first == null) {
			return second;
		}
		return second == null ? first : first + " " + second;
	}
}

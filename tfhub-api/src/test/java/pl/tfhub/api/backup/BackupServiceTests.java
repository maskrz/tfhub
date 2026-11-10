package pl.tfhub.api.backup;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BackupServiceTests {

	@TempDir
	Path temporaryDirectory;

	private BackupProperties properties;
	private BackupExecutionLock executionLock;
	private BackupExporter exporter;
	private BackupStorage storage;
	private BackupService backupService;

	@BeforeEach
	void setUp() {
		properties = new BackupProperties();
		properties.setTemporaryDirectory(temporaryDirectory);
		executionLock = new BackupExecutionLock();
		exporter = Mockito.mock(BackupExporter.class);
		storage = Mockito.mock(BackupStorage.class);
		backupService = new BackupService(properties, executionLock, exporter, storage);
	}

	@Test
	void verifiesUploadBeforePruningAndCleansTemporaryFiles() throws Exception {
		AtomicReference<String> generatedName = new AtomicReference<>();
		AtomicReference<Path> generatedDirectory = new AtomicReference<>();
		doAnswer(invocation -> {
			Path directory = invocation.getArgument(0);
			String name = invocation.getArgument(1);
			generatedName.set(name);
			generatedDirectory.set(directory);
			return Files.createFile(directory.resolve(name));
		}).when(exporter).export(any(), anyString(), any());
		var listCalls = new java.util.concurrent.atomic.AtomicInteger();
		when(storage.listBackupFiles()).thenAnswer(invocation -> {
			if (listCalls.getAndIncrement() == 0) {
				return List.of();
			}
			return List.of(generatedName.get(),
					"tfhub_backup_2025-01-03_00-00-00.xlsx",
					"tfhub_backup_2025-01-02_00-00-00.xlsx",
					"tfhub_backup_2025-01-01_00-00-00.xlsx");
		});

		BackupResponse response = backupService.execute(BackupExecutionSource.MANUAL);

		assertEquals(generatedName.get(), response.fileName());
		assertNull(response.warning());
		verify(storage).uploadAndVerify(generatedDirectory.get().resolve(response.fileName()), response.fileName());
		verify(storage).deleteBackupFile("tfhub_backup_2025-01-01_00-00-00.xlsx");
		assertFalse(Files.exists(generatedDirectory.get()));
		assertTrue(executionLock.tryAcquire());
		executionLock.release();
	}

	@Test
	void uploadFailureDoesNotApplyRetentionAndStillCleansTemporaryFiles() throws Exception {
		AtomicReference<Path> generatedDirectory = new AtomicReference<>();
		doAnswer(invocation -> {
			Path directory = invocation.getArgument(0);
			generatedDirectory.set(directory);
			String name = invocation.getArgument(1);
			return Files.createFile(directory.resolve(name));
		}).when(exporter).export(any(), anyString(), any());
		when(storage.listBackupFiles()).thenReturn(List.of("tfhub_backup_2025-01-01_00-00-00.xlsx"));
		doThrow(new BackupStorageException("private remote details"))
				.when(storage).uploadAndVerify(any(), anyString());

		assertThrows(BackupExecutionException.class,
				() -> backupService.execute(BackupExecutionSource.MANUAL));

		verify(storage, never()).deleteBackupFile(anyString());
		verify(storage).listBackupFiles();
		assertFalse(Files.exists(generatedDirectory.get()));
		assertTrue(executionLock.tryAcquire());
		executionLock.release();
	}

	@Test
	void concurrentExecutionIsRejectedWithoutStartingAnotherExport() throws Exception {
		CountDownLatch exportStarted = new CountDownLatch(1);
		CountDownLatch continueExport = new CountDownLatch(1);
		doAnswer(invocation -> {
			Path directory = invocation.getArgument(0);
			String name = invocation.getArgument(1);
			exportStarted.countDown();
			assertTrue(continueExport.await(5, TimeUnit.SECONDS));
			return Files.createFile(directory.resolve(name));
		}).when(exporter).export(any(), anyString(), any());
		when(storage.listBackupFiles()).thenReturn(List.of());

		ExecutorService executor = Executors.newSingleThreadExecutor();
		try {
			Future<BackupResponse> first = executor.submit(
					() -> backupService.execute(BackupExecutionSource.SCHEDULED));
			assertTrue(exportStarted.await(5, TimeUnit.SECONDS));
			assertThrows(BackupAlreadyRunningException.class,
					() -> backupService.execute(BackupExecutionSource.MANUAL));
			continueExport.countDown();
			assertTrue(first.get(5, TimeUnit.SECONDS).fileName().startsWith("tfhub_backup_"));
			verify(exporter, Mockito.times(1)).export(any(), anyString(), any());
		} finally {
			continueExport.countDown();
			executor.shutdownNow();
		}
	}

	@Test
	void keepsVerifiedBackupAndReturnsWarningIfRetentionDeletionFails() throws Exception {
		AtomicReference<String> generatedName = new AtomicReference<>();
		doAnswer(invocation -> {
			Path directory = invocation.getArgument(0);
			String name = invocation.getArgument(1);
			generatedName.set(name);
			return Files.createFile(directory.resolve(name));
		}).when(exporter).export(any(), anyString(), any());
		var listCalls = new java.util.concurrent.atomic.AtomicInteger();
		when(storage.listBackupFiles()).thenAnswer(invocation -> generatedName.get() == null
				? List.of()
				: listCalls.getAndIncrement() == 0
						? List.of()
						: List.of(generatedName.get(), "tfhub_backup_2025-01-03_00-00-00.xlsx",
						"tfhub_backup_2025-01-02_00-00-00.xlsx",
						"tfhub_backup_2025-01-01_00-00-00.xlsx"));
		doThrow(new BackupStorageException("remote delete failed"))
				.when(storage).deleteBackupFile("tfhub_backup_2025-01-01_00-00-00.xlsx");

		BackupResponse response = backupService.execute(BackupExecutionSource.MANUAL);

		assertEquals(generatedName.get(), response.fileName());
		assertTrue(response.warning().contains("old backups"));
		verify(storage).uploadAndVerify(any(), anyString());
	}
}

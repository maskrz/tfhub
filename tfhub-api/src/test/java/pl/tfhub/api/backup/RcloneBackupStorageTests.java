package pl.tfhub.api.backup;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class RcloneBackupStorageTests {

	private static final String FILE_NAME = "tfhub_backup_2026-11-10_14-12-40.xlsx";

	@TempDir
	Path directory;

	private Path workbook;
	private RcloneBackupStorage storage;

	@BeforeEach
	void setUp() throws Exception {
		workbook = Files.writeString(directory.resolve(FILE_NAME), "backup data");
		Path config = Files.createFile(directory.resolve("rclone.conf"));
		BackupProperties properties = new BackupProperties();
		properties.setRcloneConfig(config.toString());
		storage = new RcloneBackupStorage(properties);
	}

	@Test
	void checksOnlyTheUploadedFileAndRequiresAnExplicitMatch() throws Exception {
		List<List<String>> commands = new ArrayList<>();
		try (MockedConstruction<ProcessBuilder> ignored = processes(commands, "= " + FILE_NAME + "\n", 0)) {
			storage.uploadAndVerify(workbook, FILE_NAME);
		}

		assertEquals(2, commands.size());
		assertEquals(List.of("copyto", "--immutable", workbook.toString(), "tfhub-crypt:./" + FILE_NAME),
				arguments(commands.get(0)));
		assertEquals(List.of("check", directory.toAbsolutePath().toString(), "tfhub-crypt:.",
				"--download", "--one-way", "--include", "/" + FILE_NAME, "--combined", "-"),
				arguments(commands.get(1)));
	}

	@Test
	void verificationFailureDeletesOnlyTheAttemptedUpload() throws Exception {
		List<List<String>> commands = new ArrayList<>();
		try (MockedConstruction<ProcessBuilder> ignored = processes(commands, "* " + FILE_NAME + "\n", 1)) {
			assertThrows(BackupStorageException.class, () -> storage.uploadAndVerify(workbook, FILE_NAME));
		}

		assertEquals(3, commands.size());
		assertEquals(List.of("deletefile", "tfhub-crypt:./" + FILE_NAME), arguments(commands.get(2)));
	}

	@Test
	void emptyVerificationReportFailsAndDeletesAttemptedUpload() throws Exception {
		List<List<String>> commands = new ArrayList<>();
		try (MockedConstruction<ProcessBuilder> ignored = processes(commands, "", 0)) {
			assertThrows(BackupStorageException.class, () -> storage.uploadAndVerify(workbook, FILE_NAME));
		}

		assertEquals(3, commands.size());
		assertEquals(List.of("deletefile", "tfhub-crypt:./" + FILE_NAME), arguments(commands.get(2)));
	}

	@Test
	void rejectsMismatchedLocalNameBeforeUploading() throws Exception {
		Path otherFile = Files.writeString(directory.resolve("other.xlsx"), "backup data");

		assertThrows(BackupStorageException.class, () -> storage.uploadAndVerify(otherFile, FILE_NAME));
	}

	private MockedConstruction<ProcessBuilder> processes(
			List<List<String>> commands, String checkOutput, int checkExitCode) {
		return Mockito.mockConstruction(ProcessBuilder.class, (builder, context) -> {
			@SuppressWarnings("unchecked")
			List<String> command = List.copyOf((List<String>) context.arguments().getFirst());
			commands.add(command);
			boolean check = arguments(command).getFirst().equals("check");
			Process process = Mockito.mock(Process.class);
			when(builder.redirectErrorStream(true)).thenReturn(builder);
			when(builder.start()).thenReturn(process);
			when(process.getInputStream()).thenReturn(new ByteArrayInputStream(
					(check ? checkOutput : "").getBytes(StandardCharsets.UTF_8)));
			when(process.waitFor(anyLong(), eq(TimeUnit.SECONDS))).thenReturn(true);
			when(process.exitValue()).thenReturn(check ? checkExitCode : 0);
		});
	}

	private List<String> arguments(List<String> command) {
		return command.subList(5, command.size());
	}
}

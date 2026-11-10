package pl.tfhub.api.backup;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;

@Component
public class RcloneBackupStorage implements BackupStorage {

	private static final int MAX_CAPTURED_OUTPUT_BYTES = 1_048_576;
	private final BackupProperties properties;

	public RcloneBackupStorage(BackupProperties properties) {
		this.properties = properties;
	}

	@Override
	public void uploadAndVerify(Path localFile, String fileName) {
		requireBackupFileName(fileName);
		if (!Files.isRegularFile(localFile)) {
			throw new BackupStorageException("Generated XLSX file is unavailable for upload");
		}
		String remoteFile = remotePath(fileName);
		run("copyto", "--immutable", localFile.toString(), remoteFile);
		try {
			run("check", localFile.toString(), remoteFile, "--download");
		} catch (BackupStorageException exception) {
			try {
				run("deletefile", remoteFile);
			} catch (BackupStorageException cleanupFailure) {
				exception.addSuppressed(cleanupFailure);
			}
			throw exception;
		}
	}

	@Override
	public List<String> listBackupFiles() {
		run("mkdir", remotePath(""));
		String output = run("lsf", "--files-only", "--format", "p", remotePath(""));
		return output.lines()
				.map(String::trim)
				.filter(BackupFileNames::isValid)
				.toList();
	}

	@Override
	public void deleteBackupFile(String fileName) {
		requireBackupFileName(fileName);
		run("deletefile", remotePath(fileName));
	}

	private String remotePath(String fileName) {
		String path = properties.getRclonePath().trim();
		if (path.startsWith("/") || Arrays.stream(path.split("/")).anyMatch(".."::equals)) {
			throw new BackupStorageException("Configured backup destination path is invalid");
		}
		String remote = properties.getRcloneRemote().trim();
		if (!remote.matches("[A-Za-z0-9_-]+")) {
			throw new BackupStorageException("Configured rclone remote name is invalid");
		}
		String root = remote + ":" + path.replaceAll("^/+|/+$", "");
		if (fileName.isEmpty()) {
			return root;
		}
		return root.endsWith(":") ? root + fileName : root + "/" + fileName;
	}

	private void requireBackupFileName(String fileName) {
		if (!BackupFileNames.isValid(fileName)) {
			throw new BackupStorageException("Backup file name is invalid");
		}
	}

	private String run(String... arguments) {
		Path config = Path.of(properties.getRcloneConfig());
		if (!Files.isRegularFile(config) || !Files.isReadable(config)) {
			throw new BackupStorageException("rclone configuration is unavailable");
		}
		List<String> command = new ArrayList<>();
		command.add(properties.getRcloneExecutable());
		command.add("--config");
		command.add(config.toString());
		command.add("--log-level");
		command.add("ERROR");
		command.addAll(List.of(arguments));

		try {
			Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
			ByteArrayOutputStream output = new ByteArrayOutputStream();
			AtomicReference<IOException> outputFailure = new AtomicReference<>();
			AtomicBoolean outputTruncated = new AtomicBoolean();
			Thread outputReader = new Thread(
					() -> copyOutput(process.getInputStream(), output, outputFailure, outputTruncated),
					"rclone-output-reader");
			outputReader.setDaemon(true);
			outputReader.start();
			boolean finished = process.waitFor(properties.getCommandTimeoutSeconds(), TimeUnit.SECONDS);
			if (!finished) {
				process.destroyForcibly();
				process.waitFor();
				throw new BackupStorageException("rclone operation timed out");
			}
			outputReader.join(TimeUnit.SECONDS.toMillis(5));
			if (outputReader.isAlive()) {
				throw new BackupStorageException("Unable to read rclone command output");
			}
			if (outputFailure.get() != null) {
				throw new BackupStorageException("Unable to read rclone command output", outputFailure.get());
			}
			if (outputTruncated.get()) {
				throw new BackupStorageException("rclone command output exceeded the safe processing limit");
			}
			String result = output.toString(StandardCharsets.UTF_8);
			if (process.exitValue() != 0) {
				throw new BackupStorageException("rclone " + arguments[0]
						+ " failed with exit code " + process.exitValue());
			}
			return result;
		} catch (IOException exception) {
			throw new BackupStorageException("Unable to execute rclone", exception);
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new BackupStorageException("rclone operation was interrupted", exception);
		}
	}

	private void copyOutput(
			InputStream input,
			ByteArrayOutputStream output,
			AtomicReference<IOException> outputFailure,
			AtomicBoolean outputTruncated) {
		try (input) {
			byte[] buffer = new byte[1024];
			int read;
			int captured = 0;
			while ((read = input.read(buffer)) != -1) {
				int toCapture = Math.min(read, MAX_CAPTURED_OUTPUT_BYTES - captured);
				if (toCapture > 0) {
					output.write(buffer, 0, toCapture);
					captured += toCapture;
				}
				if (toCapture < read) {
					outputTruncated.set(true);
				}
			}
		} catch (IOException exception) {
			outputFailure.set(exception);
		}
	}
}

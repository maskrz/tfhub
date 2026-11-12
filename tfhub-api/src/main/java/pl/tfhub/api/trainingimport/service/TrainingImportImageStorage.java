package pl.tfhub.api.trainingimport.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import pl.tfhub.api.trainingimport.config.TrainingImportProperties;
import pl.tfhub.api.trainingimport.exception.TrainingImportImageStorageException;

@Component
public class TrainingImportImageStorage {

	private static final Logger LOGGER = LoggerFactory.getLogger(TrainingImportImageStorage.class);
	private static final Set<PosixFilePermission> DIRECTORY_PERMISSIONS = Set.of(
			PosixFilePermission.OWNER_READ,
			PosixFilePermission.OWNER_WRITE,
			PosixFilePermission.OWNER_EXECUTE);
	private static final Set<PosixFilePermission> FILE_PERMISSIONS = Set.of(
			PosixFilePermission.OWNER_READ,
			PosixFilePermission.OWNER_WRITE);
	private final Path storageRoot;

	public TrainingImportImageStorage(TrainingImportProperties properties) {
		this.storageRoot = properties.getStoragePath().toAbsolutePath().normalize();
	}

	public String store(byte[] jpegContents) {
		Path temporaryFile = null;
		try {
			Files.createDirectories(storageRoot);
			restrictPermissions(storageRoot, DIRECTORY_PERMISSIONS);
			temporaryFile = Files.createTempFile(storageRoot, ".training-import-", ".tmp");
			restrictPermissions(temporaryFile, FILE_PERMISSIONS);
			Files.write(temporaryFile, jpegContents);
			String storageKey = UUID.randomUUID() + ".jpg";
			Path destination = resolveStorageKey(storageKey);
			Files.move(temporaryFile, destination, StandardCopyOption.ATOMIC_MOVE);
			return storageKey;
		} catch (IOException exception) {
			throw new TrainingImportImageStorageException(exception);
		} finally {
			deleteTemporaryFile(temporaryFile);
		}
	}

	public void deleteStored(String storageKey) {
		try {
			Files.deleteIfExists(resolveStorageKey(storageKey));
		} catch (IOException exception) {
			throw new TrainingImportImageStorageException(exception);
		}
	}

	public int removeOrphanFiles(Set<String> referencedStorageKeys) {
		if (Files.notExists(storageRoot)) {
			return 0;
		}
		Set<String> referencedKeys = new HashSet<>(referencedStorageKeys);
		int removedCount = 0;
		try (var files = Files.list(storageRoot)) {
			for (Path file : files.toList()) {
				String filename = file.getFileName().toString();
				boolean temporaryFile = filename.startsWith(".training-import-") && filename.endsWith(".tmp");
				boolean orphanedImage = filename.matches("[0-9a-fA-F-]{36}\\.jpg")
						&& !referencedKeys.contains(filename);
				if ((temporaryFile || orphanedImage) && Files.deleteIfExists(file)) {
					removedCount++;
				}
			}
		} catch (IOException exception) {
			throw new TrainingImportImageStorageException(exception);
		}
		if (removedCount > 0) {
			LOGGER.warn("Removed {} orphaned training import image files", removedCount);
		}
		return removedCount;
	}

	private Path resolveStorageKey(String storageKey) {
		if (storageKey == null || !storageKey.matches("[0-9a-fA-F-]{36}\\.jpg")) {
			throw new IllegalArgumentException("Invalid training import storage key");
		}
		Path resolved = storageRoot.resolve(storageKey).normalize();
		if (!resolved.startsWith(storageRoot)) {
			throw new IllegalArgumentException("Training import storage key is outside its storage directory");
		}
		return resolved;
	}

	private void deleteTemporaryFile(Path temporaryFile) {
		if (temporaryFile == null) {
			return;
		}
		try {
			Files.deleteIfExists(temporaryFile);
		} catch (IOException exception) {
			LOGGER.error("Could not remove temporary training image file", exception);
		}
	}

	private void restrictPermissions(Path path, Set<PosixFilePermission> permissions) throws IOException {
		try {
			Files.setPosixFilePermissions(path, permissions);
		} catch (UnsupportedOperationException exception) {
			LOGGER.debug("POSIX file permissions are not supported for training image storage");
		}
	}
}

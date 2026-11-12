package pl.tfhub.api.trainingimport.service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pl.tfhub.api.trainingimport.config.TrainingImportProperties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrainingImportImageStorageTests {

	@Test
	void removesOnlyUnreferencedGeneratedImagesAndStaleTemporaryFiles(@TempDir Path directory) throws Exception {
		TrainingImportProperties properties = new TrainingImportProperties();
		properties.setStoragePath(directory);
		TrainingImportImageStorage storage = new TrainingImportImageStorage(properties);
		String retainedKey = storage.store(new byte[] {1, 2, 3});
		String orphanedKey = storage.store(new byte[] {4, 5, 6});
		Path staleTemporaryFile = directory.resolve(".training-import-stale.tmp");
		Files.write(staleTemporaryFile, new byte[] {7});

		assertEquals(2, storage.removeOrphanFiles(Set.of(retainedKey)));
		assertTrue(Files.exists(directory.resolve(retainedKey)));
		assertFalse(Files.exists(directory.resolve(orphanedKey)));
		assertFalse(Files.exists(staleTemporaryFile));
	}
}

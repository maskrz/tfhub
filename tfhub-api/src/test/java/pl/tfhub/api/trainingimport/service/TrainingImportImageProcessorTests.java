package pl.tfhub.api.trainingimport.service;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;
import pl.tfhub.api.trainingimport.config.TrainingImportProperties;
import pl.tfhub.api.trainingimport.exception.InvalidTrainingImportUploadException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrainingImportImageProcessorTests {

	private TrainingImportProperties properties;
	private TrainingImportImageProcessor imageProcessor;

	@BeforeEach
	void setUp() {
		properties = new TrainingImportProperties();
		imageProcessor = new TrainingImportImageProcessor(properties);
	}

	@Test
	void convertsSupportedPngToJpegAtMaximumOptimizedEdge() throws IOException {
		MockMultipartFile upload = createUpload("photo.png", "image/png", "png", 3000, 1000);

		OptimizedTrainingImage optimized = imageProcessor.optimize(upload);
		BufferedImage decoded = ImageIO.read(new java.io.ByteArrayInputStream(optimized.contents()));

		assertNotNull(decoded);
		assertEquals(2560, optimized.width());
		assertEquals(853, optimized.height());
		assertEquals(2560, decoded.getWidth());
		assertTrue(optimized.sha256().matches("[0-9a-f]{64}"));
		assertEquals("photo.png", optimized.originalFilename());
	}

	@Test
	void acceptsSupportedJpegWithoutUpscalingAndSanitizesClientFilename() throws IOException {
		MockMultipartFile upload = createUpload(
				"C:\\private\\folder\\sheet.jpg", "image/jpeg", "jpeg", 320, 200);

		OptimizedTrainingImage optimized = imageProcessor.optimize(upload);

		assertEquals(320, optimized.width());
		assertEquals(200, optimized.height());
		assertEquals("sheet.jpg", optimized.originalFilename());
		assertNotNull(ImageIO.read(new java.io.ByteArrayInputStream(optimized.contents())));
	}

	@Test
	void rejectsUnsupportedImageFormat() throws IOException {
		MockMultipartFile upload = createUpload("photo.bmp", "image/bmp", "bmp", 100, 100);

		assertThrows(InvalidTrainingImportUploadException.class, () -> imageProcessor.optimize(upload));
	}

	@Test
	void rejectsMimeMismatchCorruptedContentAndEmptyFiles() throws IOException {
		MockMultipartFile pngDeclaredAsJpeg = createUpload("photo.png", "image/jpeg", "png", 100, 100);
		MockMultipartFile corrupted = new MockMultipartFile("image", "bad.png", "image/png", new byte[] {1, 2, 3});
		MockMultipartFile empty = new MockMultipartFile("image", "empty.png", "image/png", new byte[0]);

		assertThrows(
				InvalidTrainingImportUploadException.class,
				() -> imageProcessor.optimize(pngDeclaredAsJpeg));
		assertThrows(InvalidTrainingImportUploadException.class, () -> imageProcessor.optimize(corrupted));
		assertThrows(InvalidTrainingImportUploadException.class, () -> imageProcessor.optimize(empty));
	}

	@Test
	void rejectsOversizedFilesAndImageDimensions() throws IOException {
		properties.setMaxUploadSize(DataSize.ofBytes(1));
		MockMultipartFile oversized = createUpload("photo.png", "image/png", "png", 100, 100);
		assertThrows(InvalidTrainingImportUploadException.class, () -> imageProcessor.optimize(oversized));

		properties.setMaxUploadSize(DataSize.ofMegabytes(15));
		properties.setMaxImageWidth(100);
		MockMultipartFile excessiveWidth = createUpload("large.png", "image/png", "png", 101, 100);
		assertThrows(InvalidTrainingImportUploadException.class, () -> imageProcessor.optimize(excessiveWidth));

		properties.setMaxImageWidth(8000);
		properties.setMaxImagePixels(10_000);
		MockMultipartFile excessivePixels = createUpload("large.png", "image/png", "png", 101, 100);
		assertThrows(InvalidTrainingImportUploadException.class, () -> imageProcessor.optimize(excessivePixels));
	}

	private MockMultipartFile createUpload(
			String filename,
			String mediaType,
			String format,
			int width,
			int height) throws IOException {
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		Graphics2D graphics = image.createGraphics();
		try {
			graphics.setColor(Color.WHITE);
			graphics.fillRect(0, 0, width, height);
			graphics.setColor(Color.BLACK);
			graphics.drawLine(0, 0, width - 1, height - 1);
		} finally {
			graphics.dispose();
		}
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		ImageIO.write(image, format, bytes);
		return new MockMultipartFile("image", filename, mediaType, bytes.toByteArray());
	}
}

package pl.tfhub.api.trainingimport.service;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.Locale;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import pl.tfhub.api.trainingimport.config.TrainingImportProperties;
import pl.tfhub.api.trainingimport.exception.InvalidTrainingImportUploadException;

@Component
public class TrainingImportImageProcessor {

	private static final int MAX_OPTIMIZED_EDGE = 2560;
	private static final float JPEG_QUALITY = 0.85f;

	private final TrainingImportProperties properties;

	public TrainingImportImageProcessor(TrainingImportProperties properties) {
		this.properties = properties;
	}

	public OptimizedTrainingImage optimize(MultipartFile upload) {
		if (upload == null || upload.isEmpty()) {
			throw new InvalidTrainingImportUploadException("A non-empty image is required");
		}
		if (upload.getSize() > properties.getMaxUploadSize().toBytes()) {
			throw new InvalidTrainingImportUploadException("The uploaded image exceeds the configured size limit");
		}

		byte[] inputBytes;
		try {
			inputBytes = upload.getBytes();
		} catch (IOException exception) {
			throw new InvalidTrainingImportUploadException("The uploaded image could not be read");
		}
		if (inputBytes.length > properties.getMaxUploadSize().toBytes()) {
			throw new InvalidTrainingImportUploadException("The uploaded image exceeds the configured size limit");
		}

		try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(inputBytes))) {
			if (input == null) {
				throw invalidImage();
			}
			Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
			if (!readers.hasNext()) {
				throw invalidImage();
			}

			ImageReader reader = readers.next();
			try {
				reader.setInput(input, true, true);
				String actualMediaType = mediaTypeFor(reader.getFormatName());
				validateDeclaredMediaType(upload.getContentType(), actualMediaType);

				int inputWidth = reader.getWidth(0);
				int inputHeight = reader.getHeight(0);
				long pixelCount = (long) inputWidth * inputHeight;
				if (inputWidth <= 0
						|| inputHeight <= 0
						|| inputWidth > properties.getMaxImageWidth()
						|| inputHeight > properties.getMaxImageHeight()
						|| pixelCount > properties.getMaxImagePixels()) {
					throw new InvalidTrainingImportUploadException(
							"The uploaded image exceeds the configured dimension limit");
				}

				ImageReadParam readParam = reader.getDefaultReadParam();
				BufferedImage decoded = reader.read(0, readParam);
				if (decoded == null) {
					throw invalidImage();
				}
				try {
					BufferedImage optimized = resizeAndFlatten(decoded);
					try {
						byte[] jpeg = encodeJpeg(optimized);
						return new OptimizedTrainingImage(
								jpeg,
								sanitizeOriginalFilename(upload.getOriginalFilename()),
								optimized.getWidth(),
								optimized.getHeight(),
								sha256(jpeg));
					} finally {
						optimized.flush();
					}
				} finally {
					decoded.flush();
				}
			} finally {
				reader.dispose();
			}
		} catch (IOException exception) {
			throw invalidImage();
		}
	}

	private BufferedImage resizeAndFlatten(BufferedImage source) {
		double scale = Math.min(1.0, (double) MAX_OPTIMIZED_EDGE / Math.max(source.getWidth(), source.getHeight()));
		int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
		int height = Math.max(1, (int) Math.round(source.getHeight() * scale));
		BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		Graphics2D graphics = result.createGraphics();
		try {
			graphics.setColor(Color.WHITE);
			graphics.fillRect(0, 0, width, height);
			graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			graphics.drawImage(source, 0, 0, width, height, null);
		} finally {
			graphics.dispose();
		}
		return result;
	}

	private byte[] encodeJpeg(BufferedImage image) throws IOException {
		Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
		if (!writers.hasNext()) {
			throw new IOException("JPEG encoder is unavailable");
		}
		ImageWriter writer = writers.next();
		try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
				ImageOutputStream output = ImageIO.createImageOutputStream(bytes)) {
			if (output == null) {
				throw new IOException("JPEG output could not be initialized");
			}
			writer.setOutput(output);
			ImageWriteParam parameters = writer.getDefaultWriteParam();
			parameters.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
			parameters.setCompressionQuality(JPEG_QUALITY);
			writer.write(null, new IIOImage(image, null, null), parameters);
			output.flush();
			return bytes.toByteArray();
		} finally {
			writer.dispose();
		}
	}

	private String mediaTypeFor(String formatName) {
		return switch (formatName.toLowerCase(Locale.ROOT)) {
			case "jpg", "jpeg" -> "image/jpeg";
			case "png" -> "image/png";
			default -> throw new InvalidTrainingImportUploadException("The image format is not supported");
		};
	}

	private void validateDeclaredMediaType(String declaredType, String actualType) {
		if (declaredType == null || declaredType.isBlank()) {
			return;
		}
		String normalizedType = declaredType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
		boolean matches = actualType.equals(normalizedType)
				|| (actualType.equals("image/jpeg") && normalizedType.equals("image/jpg"));
		if (!matches) {
			throw new InvalidTrainingImportUploadException(
					"The image content does not match its declared media type");
		}
	}

	private String sanitizeOriginalFilename(String filename) {
		String normalized = filename == null ? "" : filename.replace('\\', '/');
		int separator = normalized.lastIndexOf('/');
		String basename = normalized.substring(separator + 1);
		StringBuilder safeName = new StringBuilder();
		basename.codePoints()
				.filter(character -> !Character.isISOControl(character))
				.forEach(safeName::appendCodePoint);
		String result = safeName.toString().trim();
		if (result.isEmpty()) {
			return "upload";
		}
		int end = result.offsetByCodePoints(0, Math.min(255, result.codePointCount(0, result.length())));
		return result.substring(0, end);
	}

	private String sha256(byte[] bytes) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is unavailable", exception);
		}
	}

	private InvalidTrainingImportUploadException invalidImage() {
		return new InvalidTrainingImportUploadException("The uploaded file is not a readable supported image");
	}
}

package pl.tfhub.api.trainingimport.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "training_import_source_images")
public class TrainingImportSourceImage {

	@Id
	@Column(name = "import_id")
	private Long importId;

	@MapsId
	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "import_id", nullable = false)
	private TrainingImport trainingImport;

	@Column(name = "storage_key", nullable = false, unique = true, length = 255, updatable = false)
	private String storageKey;

	@Column(name = "media_type", nullable = false, length = 50, updatable = false)
	private String mediaType;

	@Column(name = "original_filename", nullable = false, length = 255, updatable = false)
	private String originalFilename;

	@Column(name = "byte_size", nullable = false, updatable = false)
	private long byteSize;

	@Column(nullable = false, updatable = false)
	private int width;

	@Column(nullable = false, updatable = false)
	private int height;

	@Column(nullable = false, length = 64, updatable = false)
	private String sha256;

	protected TrainingImportSourceImage() {
	}

	public TrainingImportSourceImage(
			TrainingImport trainingImport,
			String storageKey,
			String originalFilename,
			long byteSize,
			int width,
			int height,
			String sha256) {
		this.trainingImport = trainingImport;
		this.storageKey = storageKey;
		this.mediaType = "image/jpeg";
		this.originalFilename = originalFilename;
		this.byteSize = byteSize;
		this.width = width;
		this.height = height;
		this.sha256 = sha256;
		trainingImport.attachSourceImage(this);
	}

	public Long getImportId() {
		return importId;
	}

	public TrainingImport getTrainingImport() {
		return trainingImport;
	}

	public String getStorageKey() {
		return storageKey;
	}

	public String getMediaType() {
		return mediaType;
	}

	public String getOriginalFilename() {
		return originalFilename;
	}

	public long getByteSize() {
		return byteSize;
	}

	public int getWidth() {
		return width;
	}

	public int getHeight() {
		return height;
	}

	public String getSha256() {
		return sha256;
	}
}

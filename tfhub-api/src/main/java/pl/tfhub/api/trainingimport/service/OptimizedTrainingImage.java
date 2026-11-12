package pl.tfhub.api.trainingimport.service;

public record OptimizedTrainingImage(
		byte[] contents,
		String originalFilename,
		int width,
		int height,
		String sha256) {

	public OptimizedTrainingImage {
		contents = contents.clone();
	}

	@Override
	public byte[] contents() {
		return contents.clone();
	}
}

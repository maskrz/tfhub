package pl.tfhub.api.trainingimport.service;

import java.util.LinkedHashMap;
import java.util.Collections;
import java.util.Map;

final class TrainingImportRecognitionMetadata {

	private TrainingImportRecognitionMetadata() {
	}

	static boolean isUncertain(Map<String, Object> metadata) {
		return Boolean.TRUE.equals(metadata.get("uncertain"));
	}

	static boolean isReviewed(Map<String, Object> metadata) {
		return Boolean.TRUE.equals(metadata.get("manuallyReviewed"));
	}

	static Map<String, Object> withReviewState(Map<String, Object> metadata, boolean reviewed) {
		Map<String, Object> updated = new LinkedHashMap<>(metadata);
		updated.put("manuallyReviewed", reviewed);
		return Collections.unmodifiableMap(updated);
	}
}

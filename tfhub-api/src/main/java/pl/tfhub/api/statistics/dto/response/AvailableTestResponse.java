package pl.tfhub.api.statistics.dto.response;

import pl.tfhub.api.test.enums.TestCategory;

public record AvailableTestResponse(
		Long id,
		String name,
		TestCategory category,
		String unit,
		boolean active) {
}

package pl.tfhub.api.test.dto.response;

import pl.tfhub.api.test.entity.AthleticTest;
import pl.tfhub.api.test.enums.TestCategory;

public record TestResponse(
		Long id,
		String name,
		String unit,
		TestCategory category,
		boolean higherIsBetter,
		boolean active) {

	public static TestResponse from(AthleticTest test) {
		return new TestResponse(
				test.getId(),
				test.getName(),
				test.getUnit(),
				test.getCategory(),
				test.isHigherIsBetter(),
				test.isActive());
	}
}

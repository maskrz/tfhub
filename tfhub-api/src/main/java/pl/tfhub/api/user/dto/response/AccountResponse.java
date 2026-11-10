package pl.tfhub.api.user.dto.response;

import java.util.List;
import pl.tfhub.api.user.enums.UserRole;

public record AccountResponse(
		long id,
		String username,
		UserRole role,
		boolean active,
		List<TrainingGroupAssignment> trainingGroups,
		AthleteAssignment athlete) {

	public record TrainingGroupAssignment(long id, String name) {
	}

	public record AthleteAssignment(
			long id,
			String firstName,
			String lastName,
			int birthYear,
			Long trainingGroupId,
			String trainingGroupName) {
	}
}

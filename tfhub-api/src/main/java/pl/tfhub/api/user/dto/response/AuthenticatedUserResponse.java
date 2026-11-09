package pl.tfhub.api.user.dto.response;

import pl.tfhub.api.user.enums.UserRole;

public record AuthenticatedUserResponse(
		boolean authenticated,
		long userId,
		String username,
		UserRole role,
		Long athleteId) {
}

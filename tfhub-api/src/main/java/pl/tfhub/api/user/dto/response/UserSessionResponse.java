package pl.tfhub.api.user.dto.response;

import java.time.Instant;

public record UserSessionResponse(
		String id,
		Instant createdAt,
		Instant lastActivityAt,
		boolean current) {
}

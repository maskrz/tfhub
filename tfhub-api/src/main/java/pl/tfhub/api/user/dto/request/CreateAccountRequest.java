package pl.tfhub.api.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import pl.tfhub.api.user.enums.UserRole;

public record CreateAccountRequest(
		@NotBlank @Size(max = 100) String username,
		@NotBlank @Size(max = 1024) String password,
		@NotNull UserRole role,
		List<@NotNull @Positive Long> trainingGroupIds,
		@Positive Long athleteId) {
}

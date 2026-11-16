package pl.tfhub.api.athlete.dto.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateAthletePzlaIdRequest(
		@Size(max = 20) @Pattern(regexp = "[0-9]{1,20}") String pzlaId) {

	public UpdateAthletePzlaIdRequest {
		pzlaId = pzlaId == null || pzlaId.isBlank() ? null : pzlaId.strip();
	}
}

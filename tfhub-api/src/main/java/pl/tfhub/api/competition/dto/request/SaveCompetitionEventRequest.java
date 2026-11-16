package pl.tfhub.api.competition.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SaveCompetitionEventRequest(
		@NotBlank @Size(max = 150) String name,
		@NotBlank @Size(max = 10) String unit) {

	public SaveCompetitionEventRequest {
		name = name == null ? null : name.strip();
		unit = unit == null ? null : unit.strip();
	}
}

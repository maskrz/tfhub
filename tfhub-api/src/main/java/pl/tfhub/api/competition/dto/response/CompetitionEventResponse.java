package pl.tfhub.api.competition.dto.response;

import pl.tfhub.api.competition.entity.CompetitionEvent;

public record CompetitionEventResponse(Long id, String name, String unit, boolean active) {

	public static CompetitionEventResponse from(CompetitionEvent event) {
		return new CompetitionEventResponse(event.getId(), event.getName(), event.getUnit(), event.isActive());
	}
}

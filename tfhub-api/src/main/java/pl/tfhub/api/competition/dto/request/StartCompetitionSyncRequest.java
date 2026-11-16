package pl.tfhub.api.competition.dto.request;

import jakarta.validation.constraints.NotNull;
import pl.tfhub.api.competition.enums.CompetitionSyncMode;

public record StartCompetitionSyncRequest(@NotNull CompetitionSyncMode mode) {
}

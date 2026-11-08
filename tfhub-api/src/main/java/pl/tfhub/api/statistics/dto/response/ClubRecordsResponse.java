package pl.tfhub.api.statistics.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record ClubRecordsResponse(
		Long testId,
		Integer season,
		@JsonProperty("FEMALE") List<ClubRecordResponse> female,
		@JsonProperty("MALE") List<ClubRecordResponse> male) {
}

package pl.tfhub.api.result.dto.response;

import java.util.List;

public record CreateMultipleResultsResponse(int createdCount, List<ResultResponse> results) {
}

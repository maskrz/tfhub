package pl.tfhub.api.result.dto;

import java.util.List;

public record CreateMultipleResultsResponse(int createdCount, List<ResultResponse> results) {
}

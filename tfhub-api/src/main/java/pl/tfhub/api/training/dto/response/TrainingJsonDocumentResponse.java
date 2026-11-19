package pl.tfhub.api.training.dto.response;

import java.util.List;
import tools.jackson.databind.JsonNode;

public record TrainingJsonDocumentResponse(int formatVersion, List<JsonNode> trainings) {
}

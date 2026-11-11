package pl.tfhub.api.training.dto.response;

import java.math.BigDecimal;
import pl.tfhub.api.training.enums.TrainingExecutionStatus;

public record TrainingExecutionDetailsResponse(
        long id,
        long segmentId,
        int segmentSequenceNumber,
        TrainingExecutionStatus status,
        BigDecimal measuredResult,
        boolean usesSpikes) {
}

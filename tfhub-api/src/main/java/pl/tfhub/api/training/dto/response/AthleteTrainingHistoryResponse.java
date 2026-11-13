package pl.tfhub.api.training.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import pl.tfhub.api.training.enums.TrainingExecutionStatus;

public record AthleteTrainingHistoryResponse(
        long trainingId,
        LocalDate trainingDate,
        boolean canViewDetails,
        int totalSegments,
        List<Segment> segments) {

    public record Segment(
            long segmentId,
            int sequenceNumber,
            int distanceM,
            TrainingExecutionStatus status,
            BigDecimal measuredResult,
            boolean usesSpikes) {
    }
}

package pl.tfhub.api.training.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.tfhub.api.athlete.exception.InvalidAthleteIdException;
import pl.tfhub.api.common.authorization.AccessScopeService;
import pl.tfhub.api.training.dto.response.AthleteTrainingHistoryResponse;
import pl.tfhub.api.training.exception.InvalidTrainingRequestException;
import pl.tfhub.api.training.repository.AthleteTrainingHistoryRepository;
import pl.tfhub.api.user.enums.UserRole;

@Service
public class AthleteTrainingHistoryService {

    private final AthleteTrainingHistoryRepository repository;
    private final AccessScopeService accessScopeService;

    public AthleteTrainingHistoryService(
            AthleteTrainingHistoryRepository repository, AccessScopeService accessScopeService) {
        this.repository = repository;
        this.accessScopeService = accessScopeService;
    }

    @Transactional(readOnly = true)
    public List<AthleteTrainingHistoryResponse> getHistory(long athleteId, LocalDate fromDate, LocalDate toDate) {
        if (athleteId <= 0) throw new InvalidAthleteIdException();
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw new InvalidTrainingRequestException("Start date must not be after end date");
        }
        accessScopeService.requireAthleteAccess(athleteId);
        UserRole role = accessScopeService.currentUser().role();
        List<Long> groups = role == UserRole.COACH ? accessScopeService.assignedGroupIds() : List.of();
        Map<Long, List<AthleteTrainingHistoryRepository.Row>> byTraining = new LinkedHashMap<>();
        for (var row : repository.findHistory(athleteId, fromDate, toDate)) {
            byTraining.computeIfAbsent(row.trainingId(), ignored -> new ArrayList<>()).add(row);
        }
        return byTraining.values().stream().map(rows -> {
            var first = rows.getFirst();
            return new AthleteTrainingHistoryResponse(
                    first.trainingId(), first.trainingDate(),
                    role == UserRole.ADMIN || (role == UserRole.COACH && groups.contains(first.trainingGroupId())),
                    first.segmentCount(),
                    rows.stream().map(row -> new AthleteTrainingHistoryResponse.Segment(
                            row.segmentId(), row.sequenceNumber(), row.distanceM(), row.status(),
                            row.measuredResult(), row.usesSpikes())).toList());
        }).toList();
    }
}

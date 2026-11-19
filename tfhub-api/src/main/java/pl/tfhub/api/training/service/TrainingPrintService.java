package pl.tfhub.api.training.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import pl.tfhub.api.common.authorization.AccessScopeService;
import pl.tfhub.api.training.dto.response.TrainingPrintResponse;
import pl.tfhub.api.training.entity.Training;
import pl.tfhub.api.training.entity.TrainingDefinition;
import pl.tfhub.api.training.enums.TrainingType;
import pl.tfhub.api.training.exception.TrainingNotFoundException;
import pl.tfhub.api.training.repository.TrainingAssignmentRepository;
import pl.tfhub.api.training.repository.TrainingDefinitionRepository;
import pl.tfhub.api.training.repository.TrainingRepository;
import pl.tfhub.api.training.repository.TrainingSectionRepository;
import pl.tfhub.api.training.repository.TrainingSegmentRepository;
import pl.tfhub.api.traininggroup.dto.response.TrainingGroupResponse;
import pl.tfhub.api.user.enums.UserRole;

@Service
public class TrainingPrintService {
    private final TrainingRepository trainingRepository;
    private final TrainingAssignmentRepository assignments;
    private final TrainingDefinitionRepository definitions;
    private final TrainingSegmentRepository segments;
    private final TrainingSectionRepository sections;
    private final AccessScopeService access;

    public TrainingPrintService(TrainingRepository trainingRepository, TrainingAssignmentRepository assignments,
            TrainingDefinitionRepository definitions, TrainingSegmentRepository segments,
            TrainingSectionRepository sections, AccessScopeService access) {
        this.trainingRepository = trainingRepository;
        this.assignments = assignments;
        this.definitions = definitions;
        this.segments = segments;
        this.sections = sections;
        this.access = access;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public TrainingPrintResponse getPrintData(List<Long> requestedIds) {
        List<Long> ids = TrainingBulkService.validatedIds(requestedIds);
        var user = access.currentUser();
        Map<Long, Training> byId = new HashMap<>();
        trainingRepository.findAllByIdIn(ids).forEach(training -> byId.put(training.getId(), training));
        long athleteId = user.role() == UserRole.ATHLETE ? access.currentAthleteId() : 0;
        for (Long id : ids) {
            Training training = byId.get(id);
            if (training == null) throw new TrainingNotFoundException(id);
            if (user.role() == UserRole.ATHLETE) {
                if (training.getTrainingType() != TrainingType.FREE_TEXT) {
                    throw new AccessDeniedException("Athlete accounts cannot access TEMPO training details");
                }
                if (!assignments.isVisibleToAthlete(id, athleteId)) throw new TrainingNotFoundException(id);
            } else if (user.role() == UserRole.COACH) {
                if (!assignments.canCoachManage(id, user.id())) throw new TrainingNotFoundException(id);
            } else if (user.role() != UserRole.ADMIN) {
                throw new AccessDeniedException("Training access is required");
            }
        }
        var allDefinitions = definitions.findAllByTraining_IdInOrderByTraining_IdAscSequenceNumberAsc(ids);
        var definitionIds = allDefinitions.stream().map(TrainingDefinition::getId).toList();
        var allSegments = definitionIds.isEmpty() ? List.<pl.tfhub.api.training.entity.TrainingSegment>of()
                : segments.findAllByTrainingDefinition_IdInOrderByTrainingDefinition_IdAscSequenceNumberAsc(definitionIds);
        var allSections = sections.findAllByTraining_IdInOrderByTraining_IdAscSequenceNumberAsc(ids);
        var athletes = user.role() == UserRole.ATHLETE ? Map.<Long, List<pl.tfhub.api.training.dto.response.TrainingAssignedAthleteResponse>>of()
                : assignments.findAssignedAthletesForTrainings(ids);
        return new TrainingPrintResponse(ids.stream().map(id -> {
            Training training = byId.get(id);
            var group = training.getTrainingGroup() == null ? null
                    : new TrainingGroupResponse(training.getTrainingGroup().getId(), training.getTrainingGroup().getName());
            var itemDefinitions = allDefinitions.stream().filter(d -> d.getTraining().getId().equals(id))
                    .map(d -> new TrainingPrintResponse.Definition(d.getName(), d.getSequenceNumber(),
                            allSegments.stream().filter(s -> s.getTrainingDefinition().getId().equals(d.getId()))
                                    .map(s -> new TrainingPrintResponse.Segment(s.getSequenceNumber(),
                                            s.getDistanceM(), s.getRestAfterSeconds())).toList())).toList();
            var itemSections = allSections.stream().filter(s -> s.getTraining().getId().equals(id))
                    .map(s -> new TrainingPrintResponse.Section(s.getTitle(), s.getContent(), s.getSequenceNumber()))
                    .toList();
            var itemAthletes = athletes.getOrDefault(id, List.of()).stream()
                    .map(a -> new TrainingPrintResponse.Athlete(a.firstName(), a.lastName())).toList();
            return new TrainingPrintResponse.Item(id, training.getName(), training.getTrainingDate(),
                    training.getTrainingType(), training.getAssignmentType(), group, itemAthletes,
                    itemDefinitions, itemSections, user.role() == UserRole.ATHLETE ? null : training.getEquipment());
        }).toList());
    }
}

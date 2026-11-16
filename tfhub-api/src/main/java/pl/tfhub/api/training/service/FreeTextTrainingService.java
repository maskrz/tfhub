package pl.tfhub.api.training.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.tfhub.api.common.authorization.AccessScopeService;
import pl.tfhub.api.athlete.service.AthleteService;
import pl.tfhub.api.training.dto.request.CreateFreeTextTrainingRequest;
import pl.tfhub.api.training.dto.request.CreateTrainingSectionRequest;
import pl.tfhub.api.training.dto.request.EditFreeTextTrainingRequest;
import pl.tfhub.api.training.dto.request.EditTrainingSectionRequest;
import pl.tfhub.api.training.dto.response.CreateTrainingResponse;
import pl.tfhub.api.training.dto.response.UpdateTrainingResponse;
import pl.tfhub.api.training.entity.Training;
import pl.tfhub.api.training.entity.TrainingSection;
import pl.tfhub.api.training.enums.TrainingType;
import pl.tfhub.api.training.enums.TrainingAssignmentType;
import pl.tfhub.api.training.exception.InvalidTrainingIdException;
import pl.tfhub.api.training.exception.InvalidTrainingRequestException;
import pl.tfhub.api.training.exception.TrainingNotFoundException;
import pl.tfhub.api.training.repository.TrainingRepository;
import pl.tfhub.api.training.repository.TrainingAssignmentRepository;
import pl.tfhub.api.training.repository.TrainingSectionRepository;
import pl.tfhub.api.traininggroup.entity.TrainingGroup;
import pl.tfhub.api.traininggroup.exception.TrainingGroupNotFoundException;
import pl.tfhub.api.traininggroup.repository.TrainingGroupRepository;
import pl.tfhub.api.user.enums.UserRole;

@Service
public class FreeTextTrainingService {

    private final TrainingRepository trainingRepository;
    private final TrainingAssignmentRepository assignmentRepository;
    private final TrainingGroupRepository trainingGroupRepository;
    private final TrainingSectionRepository sectionRepository;
    private final AccessScopeService accessScopeService;
    private final AthleteService athleteService;

    public FreeTextTrainingService(
            TrainingRepository trainingRepository,
            TrainingAssignmentRepository assignmentRepository,
            TrainingGroupRepository trainingGroupRepository,
            TrainingSectionRepository sectionRepository,
            AccessScopeService accessScopeService,
            AthleteService athleteService) {
        this.trainingRepository = trainingRepository;
        this.assignmentRepository = assignmentRepository;
        this.trainingGroupRepository = trainingGroupRepository;
        this.sectionRepository = sectionRepository;
        this.accessScopeService = accessScopeService;
        this.athleteService = athleteService;
    }

    @Transactional
    public CreateTrainingResponse create(CreateFreeTextTrainingRequest request) {
        accessScopeService.requireCoachOrAdmin();
        validateSectionOrders(request.sections().stream()
                .map(CreateTrainingSectionRequest::sequenceNumber)
                .toList());
        ResolvedAssignment assignment = resolveAssignment(
                request.assignmentType(), request.trainingGroupId(), request.athleteIds());

        Training training = trainingRepository.saveAndFlush(new Training(
                request.name(),
                request.trainingDate(),
                assignment.group(),
                TrainingType.FREE_TEXT,
                assignment.type()));
        assignmentRepository.replaceAthletes(training.getId(), assignment.athleteIds());
        List<TrainingSection> sections = request.sections().stream()
                .map(section -> new TrainingSection(
                        training, section.title(), section.content(), section.sequenceNumber()))
                .toList();
        sectionRepository.saveAllAndFlush(sections);
        return new CreateTrainingResponse(training.getId());
    }

    @Transactional
    public UpdateTrainingResponse update(long trainingId, EditFreeTextTrainingRequest request) {
        Training training = findAccessibleTrainingForUpdate(trainingId);
        requireFreeText(training);
        if (training.getStatus() == pl.tfhub.api.training.enums.TrainingStatus.COMPLETED
                && request.trainingDate() == null) {
            throw new InvalidTrainingRequestException("A completed training must have a date");
        }
        ResolvedAssignment assignment = resolveAssignment(
                request.assignmentType(), request.trainingGroupId(), request.athleteIds());
        validateSectionOrders(request.sections().stream()
                .map(EditTrainingSectionRequest::sequenceNumber)
                .toList());

        List<TrainingSection> currentSections =
                sectionRepository.findAllByTraining_IdOrderBySequenceNumberAsc(trainingId);
        Map<Long, TrainingSection> sectionsById = new HashMap<>();
        currentSections.forEach(section -> sectionsById.put(section.getId(), section));
        Set<Long> retainedIds = new HashSet<>();
        List<TrainingSection> updatedSections = new ArrayList<>();

        for (EditTrainingSectionRequest sectionRequest : request.sections()) {
            if (sectionRequest.id() == null) {
                updatedSections.add(new TrainingSection(
                        training,
                        sectionRequest.title(),
                        sectionRequest.content(),
                        sectionRequest.sequenceNumber()));
                continue;
            }
            TrainingSection section = sectionsById.get(sectionRequest.id());
            if (section == null || !retainedIds.add(sectionRequest.id())) {
                throw new InvalidTrainingRequestException(
                        "A section ID is invalid, duplicated, or belongs to another training");
            }
            section.update(sectionRequest.title(), sectionRequest.content(), sectionRequest.sequenceNumber());
            updatedSections.add(section);
        }

        training.updateDetails(request.name(), request.trainingDate());
        training.changeAssignment(assignment.type(), assignment.group());
        assignmentRepository.replaceAthletes(trainingId, assignment.athleteIds());
        List<TrainingSection> removedSections = currentSections.stream()
                .filter(section -> !retainedIds.contains(section.getId()))
                .toList();
        sectionRepository.deleteAll(removedSections);
        sectionRepository.saveAllAndFlush(updatedSections);
        return new UpdateTrainingResponse(trainingId);
    }

    @Transactional
    public UpdateTrainingResponse markCompleted(long trainingId) {
        Training training = findAccessibleTrainingForUpdate(trainingId);
        requireFreeText(training);
        if (training.getTrainingDate() == null) {
            throw new InvalidTrainingRequestException("A date is required to complete training");
        }
        training.markCompleted();
        trainingRepository.flush();
        return new UpdateTrainingResponse(trainingId);
    }

    private Training findAccessibleTrainingForUpdate(long trainingId) {
        accessScopeService.requireCoachOrAdmin();
        if (trainingId <= 0) {
            throw new InvalidTrainingIdException();
        }
        Training training = trainingRepository.findByIdForUpdate(trainingId)
                .orElseThrow(() -> new TrainingNotFoundException(trainingId));
        if (accessScopeService.currentUser().role() == UserRole.COACH
                && !assignmentRepository.canCoachManage(trainingId, accessScopeService.currentUser().id())) {
            throw new TrainingNotFoundException(trainingId);
        }
        return training;
    }

    private ResolvedAssignment resolveAssignment(
            TrainingAssignmentType assignmentType,
            Long trainingGroupId,
            List<Long> athleteIds) {
        if (assignmentType == null) {
            throw new InvalidTrainingRequestException("A FREE_TEXT training assignment type is required");
        }
        if (assignmentType == TrainingAssignmentType.GROUP) {
            if (trainingGroupId == null || trainingGroupId <= 0
                    || (athleteIds != null && !athleteIds.isEmpty())) {
                throw new InvalidTrainingRequestException(
                        "GROUP assignment requires one training group and no athlete assignments");
            }
            accessScopeService.requireGroupAssignment(trainingGroupId);
            TrainingGroup group = trainingGroupRepository.findById(trainingGroupId)
                    .orElseThrow(() -> new TrainingGroupNotFoundException(trainingGroupId));
            return new ResolvedAssignment(assignmentType, group, List.of());
        }

        if (trainingGroupId != null || athleteIds == null || athleteIds.isEmpty()) {
            throw new InvalidTrainingRequestException(
                    "ATHLETES assignment requires one or more athletes and no training group");
        }
        Set<Long> uniqueIds = new HashSet<>(athleteIds);
        if (uniqueIds.size() != athleteIds.size() || uniqueIds.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new InvalidTrainingRequestException("Athlete IDs must be positive and unique");
        }
        athleteService.validateActiveTrainingAssignees(athleteIds);
        return new ResolvedAssignment(assignmentType, null, List.copyOf(athleteIds));
    }

    private void requireFreeText(Training training) {
        if (training.getTrainingType() != TrainingType.FREE_TEXT) {
            throw new InvalidTrainingRequestException("This operation is only available for FREE_TEXT trainings");
        }
    }

    private void validateSectionOrders(List<Integer> sequenceNumbers) {
        if (sequenceNumbers == null || sequenceNumbers.isEmpty()) {
            throw new InvalidTrainingRequestException("A FREE_TEXT training must contain at least one section");
        }
        Set<Integer> orders = new HashSet<>();
        for (Integer sequenceNumber : sequenceNumbers) {
            if (sequenceNumber == null || sequenceNumber <= 0 || !orders.add(sequenceNumber)) {
                throw new InvalidTrainingRequestException("Section sequence numbers must be positive and unique");
            }
        }
    }

    private record ResolvedAssignment(
            TrainingAssignmentType type,
            TrainingGroup group,
            List<Long> athleteIds) {
    }
}

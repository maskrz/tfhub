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
import pl.tfhub.api.training.dto.request.CreateFreeTextTrainingRequest;
import pl.tfhub.api.training.dto.request.CreateTrainingSectionRequest;
import pl.tfhub.api.training.dto.request.EditFreeTextTrainingRequest;
import pl.tfhub.api.training.dto.request.EditTrainingSectionRequest;
import pl.tfhub.api.training.dto.response.CreateTrainingResponse;
import pl.tfhub.api.training.dto.response.UpdateTrainingResponse;
import pl.tfhub.api.training.entity.Training;
import pl.tfhub.api.training.entity.TrainingSection;
import pl.tfhub.api.training.enums.TrainingType;
import pl.tfhub.api.training.exception.InvalidTrainingIdException;
import pl.tfhub.api.training.exception.InvalidTrainingRequestException;
import pl.tfhub.api.training.exception.TrainingNotFoundException;
import pl.tfhub.api.training.repository.TrainingRepository;
import pl.tfhub.api.training.repository.TrainingSectionRepository;
import pl.tfhub.api.traininggroup.entity.TrainingGroup;
import pl.tfhub.api.traininggroup.exception.TrainingGroupNotFoundException;
import pl.tfhub.api.traininggroup.repository.TrainingGroupRepository;
import pl.tfhub.api.user.enums.UserRole;

@Service
public class FreeTextTrainingService {

    private final TrainingRepository trainingRepository;
    private final TrainingGroupRepository trainingGroupRepository;
    private final TrainingSectionRepository sectionRepository;
    private final AccessScopeService accessScopeService;

    public FreeTextTrainingService(
            TrainingRepository trainingRepository,
            TrainingGroupRepository trainingGroupRepository,
            TrainingSectionRepository sectionRepository,
            AccessScopeService accessScopeService) {
        this.trainingRepository = trainingRepository;
        this.trainingGroupRepository = trainingGroupRepository;
        this.sectionRepository = sectionRepository;
        this.accessScopeService = accessScopeService;
    }

    @Transactional
    public CreateTrainingResponse create(CreateFreeTextTrainingRequest request) {
        accessScopeService.requireCoachOrAdmin();
        validateSectionOrders(request.sections().stream()
                .map(CreateTrainingSectionRequest::sequenceNumber)
                .toList());
        TrainingGroup group = trainingGroupRepository.findById(request.trainingGroupId())
                .orElseThrow(() -> new TrainingGroupNotFoundException(request.trainingGroupId()));
        accessScopeService.requireGroupAssignment(request.trainingGroupId());

        Training training = trainingRepository.saveAndFlush(new Training(
                request.name(), request.trainingDate(), group, TrainingType.FREE_TEXT));
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
                && !accessScopeService.assignedGroupIds().contains(training.getTrainingGroup().getId())) {
            throw new TrainingNotFoundException(trainingId);
        }
        return training;
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
}

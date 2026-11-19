package pl.tfhub.api.training.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import pl.tfhub.api.athlete.service.AthleteService;
import pl.tfhub.api.common.authorization.AccessScopeService;
import pl.tfhub.api.training.dto.request.CreateFreeTextTrainingRequest;
import pl.tfhub.api.training.dto.request.CreateTrainingDefinitionRequest;
import pl.tfhub.api.training.dto.request.CreateTrainingRequest;
import pl.tfhub.api.training.dto.request.CreateTrainingSectionRequest;
import pl.tfhub.api.training.dto.request.CreateTrainingSegmentRequest;
import pl.tfhub.api.training.dto.request.TrainingJsonImportItemRequest;
import pl.tfhub.api.training.dto.request.TrainingJsonImportRequest;
import pl.tfhub.api.training.dto.response.CreateTrainingResponse;
import pl.tfhub.api.training.dto.response.TrainingJsonImportResponse;
import pl.tfhub.api.training.enums.TrainingAssignmentType;
import pl.tfhub.api.training.enums.TrainingType;
import pl.tfhub.api.training.exception.InvalidTrainingRequestException;

@Service
public class TrainingJsonImportService {

    private final TrainingJsonTransferService transferService;
    private final TrainingCreationService tempoCreationService;
    private final FreeTextTrainingService freeTextCreationService;
    private final AccessScopeService accessScopeService;
    private final AthleteService athleteService;

    public TrainingJsonImportService(
            TrainingJsonTransferService transferService,
            TrainingCreationService tempoCreationService,
            FreeTextTrainingService freeTextCreationService,
            AccessScopeService accessScopeService,
            AthleteService athleteService) {
        this.transferService = transferService;
        this.tempoCreationService = tempoCreationService;
        this.freeTextCreationService = freeTextCreationService;
        this.accessScopeService = accessScopeService;
        this.athleteService = athleteService;
    }

    @Transactional
    public TrainingJsonImportResponse importFile(
            MultipartFile file,
            TrainingJsonImportRequest request) {
        accessScopeService.requireCoachOrAdmin();
        TrainingJsonTransferService.ValidatedDocument document = transferService.parseFile(file);
        if (request.formatVersion() != TrainingJsonTransferService.FORMAT_VERSION
                || document.trainings().size() != request.trainings().size()) {
            throw invalid("Dane przypisań nie odpowiadają zweryfikowanemu plikowi JSON.");
        }

        List<CreationPlan> selectedPlans = new ArrayList<>();
        for (int index = 0; index < document.trainings().size(); index++) {
            TrainingJsonImportItemRequest item = request.trainings().get(index);
            if (!item.selected()) {
                continue;
            }
            TrainingJsonTransferService.ValidatedTraining training = document.trainings().get(index);
            selectedPlans.add(resolveCreation(training, item, index + 1));
        }
        if (selectedPlans.isEmpty()) {
            throw invalid("Zaznacz co najmniej jeden trening do importu.");
        }

        List<Long> createdIds = new ArrayList<>();
        for (CreationPlan plan : selectedPlans) {
            CreateTrainingResponse response;
            if (plan instanceof TempoPlan tempoPlan) {
                response = tempoCreationService.createTraining(tempoPlan.request());
            } else {
                FreeTextPlan freeTextPlan = (FreeTextPlan) plan;
                response = freeTextCreationService.createFromJsonImport(freeTextPlan.request());
            }
            createdIds.add(response.id());
        }
        return new TrainingJsonImportResponse(List.copyOf(createdIds));
    }

    private CreationPlan resolveCreation(
            TrainingJsonTransferService.ValidatedTraining training,
            TrainingJsonImportItemRequest item,
            int trainingIndex) {
        if (training.type() == TrainingType.TEMPO) {
            if (item.trainingGroupId() == null || item.trainingGroupId() <= 0
                    || (item.assignmentType() != null && item.assignmentType() != TrainingAssignmentType.GROUP)
                    || (item.athleteIds() != null && !item.athleteIds().isEmpty())) {
                throw invalid("Trening nr " + trainingIndex + " typu TEMPO wymaga jednej grupy.");
            }
            accessScopeService.requireGroupAssignment(item.trainingGroupId());
            List<CreateTrainingDefinitionRequest> definitions = new ArrayList<>();
            for (int definitionIndex = 0; definitionIndex < training.definitions().size(); definitionIndex++) {
                var definition = training.definitions().get(definitionIndex);
                List<CreateTrainingSegmentRequest> segments = new ArrayList<>();
                for (int segmentIndex = 0; segmentIndex < definition.segments().size(); segmentIndex++) {
                    var segment = definition.segments().get(segmentIndex);
                    segments.add(new CreateTrainingSegmentRequest(
                            segmentIndex + 1, segment.distanceM(), segment.restAfterSeconds()));
                }
                definitions.add(new CreateTrainingDefinitionRequest(
                        definition.name(), definitionIndex + 1, segments));
            }
            return new TempoPlan(new CreateTrainingRequest(
                    training.name(), item.trainingDate(), item.trainingGroupId(), definitions));
        }

        TrainingAssignmentType assignmentType = item.assignmentType();
        if (assignmentType == null) {
            throw invalid("Trening nr " + trainingIndex + " typu FREE_TEXT wymaga trybu przypisania.");
        }
        if (assignmentType == TrainingAssignmentType.GROUP) {
            if (item.trainingGroupId() == null || item.trainingGroupId() <= 0
                    || (item.athleteIds() != null && !item.athleteIds().isEmpty())) {
                throw invalid("Trening nr " + trainingIndex + " wymaga jednej grupy i nie może mieć zawodników.");
            }
            accessScopeService.requireGroupAssignment(item.trainingGroupId());
        } else {
            if (item.trainingGroupId() != null || item.athleteIds() == null || item.athleteIds().isEmpty()) {
                throw invalid("Trening nr " + trainingIndex + " wymaga co najmniej jednego zawodnika bez grupy.");
            }
            Set<Long> uniqueAthletes = new HashSet<>(item.athleteIds());
            if (uniqueAthletes.size() != item.athleteIds().size()
                    || uniqueAthletes.stream().anyMatch(id -> id == null || id <= 0)) {
                throw invalid("Zawodnicy treningu nr " + trainingIndex + " muszą być poprawni i unikalni.");
            }
            athleteService.validateActiveTrainingAssignees(item.athleteIds());
        }

        List<CreateTrainingSectionRequest> sections = new ArrayList<>();
        for (int sectionIndex = 0; sectionIndex < training.sections().size(); sectionIndex++) {
            var section = training.sections().get(sectionIndex);
            sections.add(new CreateTrainingSectionRequest(
                    section.title(), section.content(), sectionIndex + 1));
        }
        return new FreeTextPlan(new CreateFreeTextTrainingRequest(
                training.name(),
                item.trainingDate(),
                assignmentType,
                assignmentType == TrainingAssignmentType.GROUP ? item.trainingGroupId() : null,
                assignmentType == TrainingAssignmentType.ATHLETES ? item.athleteIds() : List.of(),
                sections,
                training.equipment()));
    }

    private InvalidTrainingRequestException invalid(String message) {
        return new InvalidTrainingRequestException(message);
    }

    private sealed interface CreationPlan permits TempoPlan, FreeTextPlan {
    }

    private record TempoPlan(CreateTrainingRequest request) implements CreationPlan {
    }

    private record FreeTextPlan(CreateFreeTextTrainingRequest request) implements CreationPlan {
    }
}

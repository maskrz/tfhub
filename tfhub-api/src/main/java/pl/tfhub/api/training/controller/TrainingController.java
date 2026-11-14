package pl.tfhub.api.training.controller;

import java.net.URI;
import java.util.List;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.tfhub.api.training.dto.request.EditTrainingRequest;
import pl.tfhub.api.training.dto.request.CreateTrainingRequest;
import pl.tfhub.api.training.dto.request.CompleteTrainingRequest;
import pl.tfhub.api.training.dto.request.CreateFreeTextTrainingRequest;
import pl.tfhub.api.training.dto.request.EditFreeTextTrainingRequest;
import pl.tfhub.api.training.dto.response.CreateTrainingResponse;
import pl.tfhub.api.training.dto.response.TrainingListItemResponse;
import pl.tfhub.api.training.dto.response.TrainingDetailsResponse;
import pl.tfhub.api.training.dto.response.UpdateTrainingResponse;
import pl.tfhub.api.training.enums.TrainingType;
import pl.tfhub.api.training.service.TrainingCreationService;
import pl.tfhub.api.training.service.TrainingDetailsService;
import pl.tfhub.api.training.service.TrainingDeletionService;
import pl.tfhub.api.training.service.TrainingCompletionService;
import pl.tfhub.api.training.service.TrainingQueryService;
import pl.tfhub.api.training.service.TrainingUpdateService;
import pl.tfhub.api.training.service.FreeTextTrainingService;

@RestController
@RequestMapping("/api/trainings")
public class TrainingController {

    private final TrainingQueryService trainingQueryService;
    private final TrainingCreationService trainingCreationService;
    private final TrainingUpdateService trainingUpdateService;
    private final TrainingDetailsService trainingDetailsService;
    private final TrainingDeletionService trainingDeletionService;
    private final TrainingCompletionService trainingCompletionService;
    private final FreeTextTrainingService freeTextTrainingService;

    public TrainingController(
            TrainingQueryService trainingQueryService,
            TrainingCreationService trainingCreationService,
            TrainingUpdateService trainingUpdateService,
            TrainingDetailsService trainingDetailsService,
            TrainingDeletionService trainingDeletionService,
            TrainingCompletionService trainingCompletionService,
            FreeTextTrainingService freeTextTrainingService) {
        this.trainingQueryService = trainingQueryService;
        this.trainingCreationService = trainingCreationService;
        this.trainingUpdateService = trainingUpdateService;
        this.trainingDetailsService = trainingDetailsService;
        this.trainingDeletionService = trainingDeletionService;
        this.trainingCompletionService = trainingCompletionService;
        this.freeTextTrainingService = freeTextTrainingService;
    }

    @GetMapping
    public List<TrainingListItemResponse> listTrainings(
            @RequestParam(defaultValue = "false") boolean includeCompleted,
            @RequestParam(required = false) TrainingType trainingType) {
        return trainingQueryService.listTrainings(includeCompleted, trainingType);
    }

    @GetMapping("/{trainingId}")
    public TrainingDetailsResponse getTraining(@PathVariable long trainingId) {
        return trainingDetailsService.getTraining(trainingId);
    }

    @DeleteMapping("/{trainingId}")
    public ResponseEntity<Void> deleteTraining(@PathVariable long trainingId) {
        trainingDeletionService.deleteTraining(trainingId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping
    public ResponseEntity<CreateTrainingResponse> createTraining(
            @Valid @RequestBody CreateTrainingRequest request) {
        CreateTrainingResponse response = trainingCreationService.createTraining(request);
        return ResponseEntity.created(URI.create("/api/trainings/" + response.id())).body(response);
    }

    @PostMapping("/free-text")
    public ResponseEntity<CreateTrainingResponse> createFreeTextTraining(
            @Valid @RequestBody CreateFreeTextTrainingRequest request) {
        CreateTrainingResponse response = freeTextTrainingService.create(request);
        return ResponseEntity.created(URI.create("/api/trainings/" + response.id())).body(response);
    }

    @PutMapping("/{trainingId}")
    public UpdateTrainingResponse updateTraining(
            @PathVariable long trainingId,
            @Valid @RequestBody EditTrainingRequest request) {
        return trainingUpdateService.updateTraining(trainingId, request);
    }

    @PutMapping("/{trainingId}/free-text")
    public UpdateTrainingResponse updateFreeTextTraining(
            @PathVariable long trainingId,
            @Valid @RequestBody EditFreeTextTrainingRequest request) {
        return freeTextTrainingService.update(trainingId, request);
    }

    @PatchMapping("/{trainingId}/complete")
    public UpdateTrainingResponse markFreeTextTrainingCompleted(@PathVariable long trainingId) {
        return freeTextTrainingService.markCompleted(trainingId);
    }

    @PatchMapping("/{trainingId}/participations")
    public UpdateTrainingResponse completeTraining(
            @PathVariable long trainingId,
            @Valid @RequestBody CompleteTrainingRequest request) {
        return trainingCompletionService.completeTraining(trainingId, request);
    }
}

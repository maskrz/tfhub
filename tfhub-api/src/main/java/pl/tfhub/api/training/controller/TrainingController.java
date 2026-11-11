package pl.tfhub.api.training.controller;

import java.net.URI;
import java.util.List;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.tfhub.api.training.dto.request.EditTrainingRequest;
import pl.tfhub.api.training.dto.request.CreateTrainingRequest;
import pl.tfhub.api.training.dto.response.CreateTrainingResponse;
import pl.tfhub.api.training.dto.response.DeactivateTrainingResponse;
import pl.tfhub.api.training.dto.response.TrainingListItemResponse;
import pl.tfhub.api.training.dto.response.TrainingDetailsResponse;
import pl.tfhub.api.training.dto.response.UpdateTrainingResponse;
import pl.tfhub.api.training.service.TrainingCreationService;
import pl.tfhub.api.training.service.TrainingDetailsService;
import pl.tfhub.api.training.service.TrainingDeactivationService;
import pl.tfhub.api.training.service.TrainingQueryService;
import pl.tfhub.api.training.service.TrainingUpdateService;

@RestController
@RequestMapping("/api/trainings")
public class TrainingController {

    private final TrainingQueryService trainingQueryService;
    private final TrainingCreationService trainingCreationService;
    private final TrainingUpdateService trainingUpdateService;
    private final TrainingDetailsService trainingDetailsService;
    private final TrainingDeactivationService trainingDeactivationService;

    public TrainingController(
            TrainingQueryService trainingQueryService,
            TrainingCreationService trainingCreationService,
            TrainingUpdateService trainingUpdateService,
            TrainingDetailsService trainingDetailsService,
            TrainingDeactivationService trainingDeactivationService) {
        this.trainingQueryService = trainingQueryService;
        this.trainingCreationService = trainingCreationService;
        this.trainingUpdateService = trainingUpdateService;
        this.trainingDetailsService = trainingDetailsService;
        this.trainingDeactivationService = trainingDeactivationService;
    }

    @GetMapping
    public List<TrainingListItemResponse> listTrainings() {
        return trainingQueryService.listTrainings();
    }

    @GetMapping("/{trainingId}")
    public TrainingDetailsResponse getTraining(@PathVariable long trainingId) {
        return trainingDetailsService.getTraining(trainingId);
    }

    @PatchMapping("/{trainingId}/deactivate")
    public DeactivateTrainingResponse deactivateTraining(@PathVariable long trainingId) {
        return trainingDeactivationService.deactivateTraining(trainingId);
    }

    @PostMapping
    public ResponseEntity<CreateTrainingResponse> createTraining(
            @Valid @RequestBody CreateTrainingRequest request) {
        CreateTrainingResponse response = trainingCreationService.createTraining(request);
        return ResponseEntity.created(URI.create("/api/trainings/" + response.id())).body(response);
    }

    @PutMapping("/{trainingId}")
    public UpdateTrainingResponse updateTraining(
            @PathVariable long trainingId,
            @Valid @RequestBody EditTrainingRequest request) {
        return trainingUpdateService.updateTraining(trainingId, request);
    }
}

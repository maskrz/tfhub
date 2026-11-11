package pl.tfhub.api.training.controller;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.tfhub.api.training.dto.response.TrainingListItemResponse;
import pl.tfhub.api.training.service.TrainingQueryService;

@RestController
@RequestMapping("/api/trainings")
public class TrainingController {

    private final TrainingQueryService trainingQueryService;

    public TrainingController(TrainingQueryService trainingQueryService) {
        this.trainingQueryService = trainingQueryService;
    }

    @GetMapping
    public List<TrainingListItemResponse> listTrainings() {
        return trainingQueryService.listTrainings();
    }
}

package pl.tfhub.api.traininggroup.controller;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.tfhub.api.traininggroup.dto.response.TrainingGroupResponse;
import pl.tfhub.api.traininggroup.service.TrainingGroupService;

@RestController
@RequestMapping("/api/training-groups")
public class TrainingGroupController {

	private final TrainingGroupService trainingGroupService;

	public TrainingGroupController(TrainingGroupService trainingGroupService) {
		this.trainingGroupService = trainingGroupService;
	}

	@GetMapping("/accessible")
	public List<TrainingGroupResponse> findAccessibleGroups() {
		return trainingGroupService.findAccessibleGroups();
	}
}

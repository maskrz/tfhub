package pl.tfhub.api.trainingimport.controller;

import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import pl.tfhub.api.trainingimport.dto.response.TrainingImportUploadResponse;
import pl.tfhub.api.trainingimport.exception.InvalidTrainingImportUploadException;
import pl.tfhub.api.trainingimport.service.TrainingImportUploadService;

@RestController
@RequestMapping("/api/training-imports")
public class TrainingImportController {

	private final TrainingImportUploadService uploadService;

	public TrainingImportController(TrainingImportUploadService uploadService) {
		this.uploadService = uploadService;
	}

	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<TrainingImportUploadResponse> upload(
			@RequestParam("trainingGroupId") long trainingGroupId,
			MultipartHttpServletRequest request) {
		MultiValueMap<String, MultipartFile> files = request.getMultiFileMap();
		List<MultipartFile> images = files.get("image");
		if (files.size() != 1 || images == null || images.size() != 1) {
			throw new InvalidTrainingImportUploadException("Exactly one image file is required");
		}
		return ResponseEntity.accepted().body(uploadService.upload(trainingGroupId, images.get(0)));
	}
}

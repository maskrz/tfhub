package pl.tfhub.api.trainingimport.controller;

import java.util.List;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import pl.tfhub.api.trainingimport.dto.response.TrainingImportUploadResponse;
import pl.tfhub.api.trainingimport.dto.response.TrainingImportStatusResponse;
import pl.tfhub.api.trainingimport.dto.response.TrainingImportAthleteCandidateResponse;
import pl.tfhub.api.trainingimport.dto.response.TrainingImportDraftResponse;
import pl.tfhub.api.trainingimport.dto.response.TrainingImportAcceptanceResponse;
import pl.tfhub.api.trainingimport.dto.response.TrainingImportRejectionResponse;
import pl.tfhub.api.trainingimport.dto.request.TrainingImportDraftUpdateRequest;
import pl.tfhub.api.trainingimport.exception.InvalidTrainingImportUploadException;
import pl.tfhub.api.trainingimport.service.TrainingImportDraftService;
import pl.tfhub.api.trainingimport.service.TrainingImportSourceImageService;
import pl.tfhub.api.trainingimport.service.TrainingImportUploadService;
import pl.tfhub.api.trainingimport.service.TrainingImportProcessingService;
import pl.tfhub.api.trainingimport.service.TrainingImportFinalizationService;

@RestController
@RequestMapping("/api/training-imports")
public class TrainingImportController {

	private final TrainingImportUploadService uploadService;
	private final TrainingImportDraftService draftService;
	private final TrainingImportSourceImageService sourceImageService;
	private final TrainingImportProcessingService processingService;
	private final TrainingImportFinalizationService finalizationService;

	public TrainingImportController(
			TrainingImportUploadService uploadService,
			TrainingImportDraftService draftService,
			TrainingImportSourceImageService sourceImageService,
			TrainingImportProcessingService processingService,
			TrainingImportFinalizationService finalizationService) {
		this.uploadService = uploadService;
		this.draftService = draftService;
		this.sourceImageService = sourceImageService;
		this.processingService = processingService;
		this.finalizationService = finalizationService;
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

	@GetMapping("/{importId}")
	public TrainingImportStatusResponse getStatus(@PathVariable long importId) {
		return processingService.getStatus(importId);
	}

	@PostMapping("/{importId}/retry")
	public ResponseEntity<TrainingImportStatusResponse> retry(@PathVariable long importId) {
		return ResponseEntity.accepted().body(processingService.retry(importId));
	}

	@PostMapping("/{importId}/accept")
	public TrainingImportAcceptanceResponse accept(@PathVariable long importId) {
		return finalizationService.accept(importId);
	}

	@PostMapping("/{importId}/reject")
	public TrainingImportRejectionResponse reject(@PathVariable long importId) {
		return finalizationService.reject(importId);
	}

	@GetMapping("/{importId}/draft")
	public TrainingImportDraftResponse getDraft(@PathVariable long importId) {
		return draftService.getDraft(importId);
	}

	@PutMapping("/{importId}/draft")
	public TrainingImportDraftResponse updateDraft(
			@PathVariable long importId,
			@Valid @RequestBody TrainingImportDraftUpdateRequest request) {
		return draftService.updateDraft(importId, request);
	}

	@GetMapping("/{importId}/athlete-candidates")
	public List<TrainingImportAthleteCandidateResponse> findAthleteCandidates(
			@PathVariable long importId,
			@RequestParam(required = false) String query) {
		return draftService.findAthleteCandidates(importId, query);
	}

	@GetMapping(value = "/{importId}/source-image", produces = MediaType.IMAGE_JPEG_VALUE)
	public ResponseEntity<byte[]> getSourceImage(@PathVariable long importId) {
		return ResponseEntity.ok()
				.contentType(MediaType.IMAGE_JPEG)
				.body(sourceImageService.getSourceImage(importId));
	}
}

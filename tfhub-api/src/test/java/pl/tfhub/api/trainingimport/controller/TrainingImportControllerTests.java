package pl.tfhub.api.trainingimport.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.mock.web.MockMultipartFile;
import pl.tfhub.api.trainingimport.dto.response.TrainingImportUploadResponse;
import pl.tfhub.api.trainingimport.dto.response.TrainingImportAthleteCandidateResponse;
import pl.tfhub.api.trainingimport.dto.response.TrainingImportDraftResponse;
import pl.tfhub.api.trainingimport.dto.response.TrainingImportStatusResponse;
import pl.tfhub.api.trainingimport.enums.TrainingImportAttemptStatus;
import pl.tfhub.api.trainingimport.enums.TrainingImportStatus;
import pl.tfhub.api.trainingimport.service.TrainingImportDraftService;
import pl.tfhub.api.trainingimport.service.TrainingImportProcessingService;
import pl.tfhub.api.trainingimport.service.TrainingImportSourceImageService;
import pl.tfhub.api.trainingimport.service.TrainingImportUploadService;
import pl.tfhub.api.traininggroup.dto.response.TrainingGroupResponse;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TrainingImportController.class)
class TrainingImportControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private TrainingImportUploadService uploadService;

	@MockitoBean
	private TrainingImportDraftService draftService;

	@MockitoBean
	private TrainingImportSourceImageService sourceImageService;

	@MockitoBean
	private TrainingImportProcessingService processingService;

	@Test
	void acceptsExactlyOneImageAndReturnsUploadDetails() throws Exception {
		when(uploadService.upload(eq(7L), any()))
				.thenReturn(new TrainingImportUploadResponse(42, 7, TrainingImportStatus.PENDING, true));

		mockMvc.perform(uploadRequest().file(image("photo.png")))
				.andExpect(status().isAccepted())
				.andExpect(content().json("""
						{
						  "id": 42,
						  "trainingGroupId": 7,
						  "status": "PENDING",
						  "possibleDuplicate": true
						}
						""", true));

		verify(uploadService).upload(eq(7L), any());
	}

	@Test
	void rejectsMissingOrMultipleImageFiles() throws Exception {
		mockMvc.perform(uploadRequest())
				.andExpect(status().isBadRequest());
		mockMvc.perform(uploadRequest()
						.file(image("first.png"))
						.file(image("second.png")))
				.andExpect(status().isBadRequest());
		mockMvc.perform(uploadRequest()
						.file(new MockMultipartFile("other", "photo.png", MediaType.IMAGE_PNG_VALUE, new byte[] {1})))
				.andExpect(status().isBadRequest());
	}

	@Test
	void retrievesDraftAndValidationState() throws Exception {
		when(draftService.getDraft(42L)).thenReturn(new TrainingImportDraftResponse(
				42,
				TrainingImportStatus.REVIEW_REQUIRED,
				new TrainingGroupResponse(7, "Group A"),
				"Intervals",
				java.time.LocalDate.parse("2026-11-10"),
				java.util.Map.of(),
				true,
				java.util.List.of(),
				new TrainingImportDraftResponse.Validation(true, java.util.List.of())));

		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
						.get("/api/training-imports/42/draft"))
				.andExpect(status().isOk())
				.andExpect(content().json("""
						{
						  "importId": 42,
						  "status": "REVIEW_REQUIRED",
						  "trainingGroup": {"id": 7, "name": "Group A"},
						  "name": "Intervals",
						  "trainingDate": "2026-11-10",
						  "recognitionMetadata": {},
						  "recognitionReviewed": true,
						  "definitions": [],
						  "validation": {"readyForAcceptance": true, "issues": []}
						}
						""", true));
	}

	@Test
	void savesCompleteDraftAndSearchesCandidatesWithinImport() throws Exception {
		when(draftService.updateDraft(eq(42L), any())).thenReturn(new TrainingImportDraftResponse(
				42,
				TrainingImportStatus.REVIEW_REQUIRED,
				new TrainingGroupResponse(7, "Group A"),
				"Intervals",
				java.time.LocalDate.parse("2026-11-10"),
				java.util.Map.of(),
				true,
				java.util.List.of(),
				new TrainingImportDraftResponse.Validation(true, java.util.List.of())));
		when(draftService.findAthleteCandidates(42L, "Ada")).thenReturn(java.util.List.of(
				new TrainingImportAthleteCandidateResponse(
						11, "Ada", "Lovelace", 1990, pl.tfhub.api.athlete.enums.Gender.FEMALE, true)));

		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
						.put("/api/training-imports/42/draft")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "name": "Intervals",
								  "trainingDate": "2026-11-10",
								  "recognitionReviewed": true,
								  "definitions": [{
								    "name": "Main set",
								    "sequenceNumber": 1,
								    "segments": [{
								      "sequenceNumber": 1,
								      "distanceM": 400,
								      "recognitionReviewed": true
								    }],
								    "athleteRows": []
								  }]
								}
								"""))
				.andExpect(status().isOk())
				.andExpect(content().json("{\"status\":\"REVIEW_REQUIRED\"}"));

		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
						.get("/api/training-imports/42/athlete-candidates")
						.param("query", "Ada"))
				.andExpect(status().isOk())
				.andExpect(content().json("""
						[{"id":11,"firstName":"Ada","lastName":"Lovelace","birthYear":1990,
						  "gender":"FEMALE","active":true}]
						"""));

		verify(draftService).updateDraft(eq(42L), any());
		verify(draftService).findAthleteCandidates(42L, "Ada");
	}

	@Test
	void returnsOptimizedSourceImageAsJpeg() throws Exception {
		when(sourceImageService.getSourceImage(42L)).thenReturn(new byte[] {(byte) 0xff, (byte) 0xd8});

		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
						.get("/api/training-imports/42/source-image"))
				.andExpect(status().isOk())
				.andExpect(content().contentType(MediaType.IMAGE_JPEG))
				.andExpect(content().bytes(new byte[] {(byte) 0xff, (byte) 0xd8}));
	}

	@Test
	void retrievesSanitizedStatusAndRetriesWithAcceptedResponse() throws Exception {
		TrainingImportStatusResponse statusResponse = new TrainingImportStatusResponse(
				42,
				7,
				TrainingImportStatus.FAILED,
				java.time.OffsetDateTime.parse("2026-11-12T10:00:00Z"),
				false,
				new TrainingImportStatusResponse.Attempt(
						2,
						TrainingImportAttemptStatus.FAILED,
						java.time.OffsetDateTime.parse("2026-11-12T10:01:00Z"),
						java.time.OffsetDateTime.parse("2026-11-12T10:02:00Z"),
						"OUTPUT_VALIDATION_FAILED",
						"The model response did not match the expected training data structure"));
		when(processingService.getStatus(42L)).thenReturn(statusResponse);
		when(processingService.retry(42L)).thenReturn(new TrainingImportStatusResponse(
				42,
				7,
				TrainingImportStatus.PROCESSING,
				statusResponse.createdAt(),
				false,
				new TrainingImportStatusResponse.Attempt(
						3,
						TrainingImportAttemptStatus.PROCESSING,
						java.time.OffsetDateTime.parse("2026-11-12T10:03:00Z"),
						null,
						null,
						null)));

		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
						.get("/api/training-imports/42"))
				.andExpect(status().isOk())
				.andExpect(content().json("""
						{
						  "id":42,
						  "trainingGroupId":7,
						  "status":"FAILED",
						  "possibleDuplicate":false,
						  "latestAttempt":{
						    "attemptNumber":2,
						    "status":"FAILED",
						    "failureCode":"OUTPUT_VALIDATION_FAILED",
						    "failureMessage":"The model response did not match the expected training data structure"
						  }
						}
						""", false));

		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
						.post("/api/training-imports/42/retry"))
				.andExpect(status().isAccepted())
				.andExpect(content().json("{\"id\":42,\"status\":\"PROCESSING\"}"));

		verify(processingService).getStatus(42L);
		verify(processingService).retry(42L);
	}

	private MockMultipartHttpServletRequestBuilder uploadRequest() {
		return multipart("/api/training-imports")
				.param("trainingGroupId", "7");
	}

	private MockMultipartFile image(String filename) {
		return new MockMultipartFile("image", filename, MediaType.IMAGE_PNG_VALUE, new byte[] {1});
	}
}

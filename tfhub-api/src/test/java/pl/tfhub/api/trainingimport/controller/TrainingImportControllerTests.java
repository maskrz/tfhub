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
import pl.tfhub.api.trainingimport.enums.TrainingImportStatus;
import pl.tfhub.api.trainingimport.service.TrainingImportUploadService;

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

	private MockMultipartHttpServletRequestBuilder uploadRequest() {
		return multipart("/api/training-imports")
				.param("trainingGroupId", "7");
	}

	private MockMultipartFile image(String filename) {
		return new MockMultipartFile("image", filename, MediaType.IMAGE_PNG_VALUE, new byte[] {1});
	}
}

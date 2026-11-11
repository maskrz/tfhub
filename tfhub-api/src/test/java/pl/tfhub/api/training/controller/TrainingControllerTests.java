package pl.tfhub.api.training.controller;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pl.tfhub.api.training.dto.response.TrainingListItemResponse;
import pl.tfhub.api.training.service.TrainingQueryService;
import pl.tfhub.api.traininggroup.dto.response.TrainingGroupResponse;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TrainingController.class)
class TrainingControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TrainingQueryService trainingQueryService;

    @Test
    void returnsTrainingSummariesFromTheService() throws Exception {
        when(trainingQueryService.listTrainings()).thenReturn(List.of(
                new TrainingListItemResponse(7, "Intervals", LocalDate.parse("2026-11-10"),
                        new TrainingGroupResponse(3, "Group A"), 2, 12)));

        mockMvc.perform(get("/api/trainings"))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        [{
                          "id": 7,
                          "name": "Intervals",
                          "trainingDate": "2026-11-10",
                          "trainingGroup": {"id": 3, "name": "Group A"},
                          "definitionCount": 2,
                          "participantCount": 12
                        }]
                        """, true));

        verify(trainingQueryService).listTrainings();
    }

    @Test
    void returnsAnEmptyCollectionWhenNoTrainingsAreVisible() throws Exception {
        when(trainingQueryService.listTrainings()).thenReturn(List.of());

        mockMvc.perform(get("/api/trainings"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]", true));
    }
}

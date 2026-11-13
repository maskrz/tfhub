package pl.tfhub.api.training.controller;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pl.tfhub.api.training.dto.response.AthleteTrainingHistoryResponse;
import pl.tfhub.api.training.enums.TrainingExecutionStatus;
import pl.tfhub.api.training.service.AthleteTrainingHistoryService;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AthleteTrainingHistoryController.class)
class AthleteTrainingHistoryControllerTests {
    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private AthleteTrainingHistoryService service;

    @Test
    void returnsHistoryWithDateBoundsAndSegmentValues() throws Exception {
        LocalDate date = LocalDate.parse("2026-11-11");
        when(service.getHistory(1, date, date)).thenReturn(List.of(new AthleteTrainingHistoryResponse(
                7, date, false, 1, List.of(new AthleteTrainingHistoryResponse.Segment(
                9, 1, 200, TrainingExecutionStatus.RECORDED, new BigDecimal("33.125"), true)))));
        mockMvc.perform(get("/api/athletes/1/training-history")
                        .queryParam("fromDate", "2026-11-11").queryParam("toDate", "2026-11-11"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].trainingId").value(7))
                .andExpect(jsonPath("$[0].canViewDetails").value(false))
                .andExpect(jsonPath("$[0].totalSegments").value(1))
                .andExpect(jsonPath("$[0].segments[0].measuredResult").value(33.125))
                .andExpect(jsonPath("$[0].segments[0].usesSpikes").value(true));
        verify(service).getHistory(1, date, date);
    }

    @Test
    void rejectsMalformedDates() throws Exception {
        mockMvc.perform(get("/api/athletes/1/training-history").queryParam("fromDate", "invalid"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/athletes/1/training-history").queryParam("fromDate", ""))
                .andExpect(status().isBadRequest());
    }
}

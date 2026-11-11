package pl.tfhub.api.training.controller;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import pl.tfhub.api.training.dto.response.TrainingListItemResponse;
import pl.tfhub.api.training.dto.response.CreateTrainingResponse;
import pl.tfhub.api.training.dto.response.TrainingDetailsResponse;
import pl.tfhub.api.training.dto.response.DeactivateTrainingResponse;
import pl.tfhub.api.training.dto.response.UpdateTrainingResponse;
import pl.tfhub.api.training.service.TrainingCreationService;
import pl.tfhub.api.training.service.TrainingDetailsService;
import pl.tfhub.api.training.service.TrainingDeactivationService;
import pl.tfhub.api.training.service.TrainingQueryService;
import pl.tfhub.api.training.service.TrainingUpdateService;
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

    @MockitoBean
    private TrainingCreationService trainingCreationService;

    @MockitoBean
    private TrainingUpdateService trainingUpdateService;

    @MockitoBean
    private TrainingDetailsService trainingDetailsService;

    @MockitoBean
    private TrainingDeactivationService trainingDeactivationService;

    @Test
    void createsTrainingAndReturnsItsIdentifierAndLocation() throws Exception {
        when(trainingCreationService.createTraining(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new CreateTrainingResponse(42));

        mockMvc.perform(MockMvcRequestBuilders.post("/api/trainings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "  Intervals ",
                                  "trainingDate": "2026-11-10",
                                  "trainingGroupId": 3,
                                  "definitions": [{
                                    "name": "Main set",
                                    "sequenceNumber": 1,
                                    "segments": [
                                      {"sequenceNumber": 1, "distanceM": 400, "restAfterSeconds": 60}
                                    ],
                                    "athleteIds": [11]
                                  }]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(MockMvcResultMatchers.header().string("Location", "/api/trainings/42"))
                .andExpect(content().json("{\"id\":42}", true));
    }

    @Test
    void rejectsMissingGroupAndEmptyDefinitionsInRequest() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/api/trainings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Intervals","trainingDate":"2026-11-10","definitions":[]}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsDefinitionWithoutSegmentsAndInvalidSegmentValues() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/api/trainings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"Intervals","trainingDate":"2026-11-10","trainingGroupId":1,
                                  "definitions":[{"name":"Main","sequenceNumber":1,"segments":[]}]
                                }
                                """))
                .andExpect(status().isBadRequest());
        mockMvc.perform(MockMvcRequestBuilders.post("/api/trainings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"Intervals","trainingDate":"2026-11-10","trainingGroupId":1,
                                  "definitions":[{"name":"Main","sequenceNumber":1,"segments":[
                                    {"sequenceNumber":1,"distanceM":0,"restAfterSeconds":-1}
                                  ]}]
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updatesTrainingAndReturnsItsIdentifier() throws Exception {
        when(trainingUpdateService.updateTraining(
                org.mockito.ArgumentMatchers.eq(42L), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new UpdateTrainingResponse(42));

        mockMvc.perform(MockMvcRequestBuilders.put("/api/trainings/42")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Updated session",
                                  "trainingDate": "2026-11-11",
                                  "definitions": [{
                                    "name": "Main set",
                                    "sequenceNumber": 1,
                                    "segments": [{
                                      "sequenceNumber": 1,
                                      "distanceM": 400
                                    }],
                                    "participations": []
                                  }]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"id\":42}", true));

        verify(trainingUpdateService).updateTraining(
                org.mockito.ArgumentMatchers.eq(42L), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void returnsTrainingDetailsFromTheService() throws Exception {
        when(trainingDetailsService.getTraining(42)).thenReturn(
                new TrainingDetailsResponse(
                        42,
                        "Intervals",
                        LocalDate.parse("2026-11-10"),
                        true,
                        new TrainingGroupResponse(3, "Group A"),
                        List.of()));

        mockMvc.perform(get("/api/trainings/42"))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {
                          "id": 42,
                          "name": "Intervals",
                          "trainingDate": "2026-11-10",
                          "active": true,
                          "trainingGroup": {"id": 3, "name": "Group A"},
                          "definitions": []
                        }
                        """, true));

        verify(trainingDetailsService).getTraining(42);
    }

    @Test
    void deactivatesTrainingAndReturnsItsIdentifier() throws Exception {
        when(trainingDeactivationService.deactivateTraining(42))
                .thenReturn(new DeactivateTrainingResponse(42));

        mockMvc.perform(MockMvcRequestBuilders.patch("/api/trainings/42/deactivate"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"id\":42}", true));

        verify(trainingDeactivationService).deactivateTraining(42);
    }

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

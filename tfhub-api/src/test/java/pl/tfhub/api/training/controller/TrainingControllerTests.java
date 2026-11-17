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
import pl.tfhub.api.training.dto.response.UpdateTrainingResponse;
import pl.tfhub.api.training.dto.request.CreateFreeTextTrainingRequest;
import pl.tfhub.api.training.dto.request.EditFreeTextTrainingRequest;
import pl.tfhub.api.training.enums.TrainingStatus;
import pl.tfhub.api.training.enums.TrainingAssignmentType;
import pl.tfhub.api.training.enums.TrainingType;
import pl.tfhub.api.training.service.TrainingCreationService;
import pl.tfhub.api.training.service.TrainingCompletionService;
import pl.tfhub.api.training.service.TrainingDetailsService;
import pl.tfhub.api.training.service.TrainingDeletionService;
import pl.tfhub.api.training.service.TrainingQueryService;
import pl.tfhub.api.training.service.TrainingUpdateService;
import pl.tfhub.api.training.service.FreeTextTrainingService;
import pl.tfhub.api.traininggroup.dto.response.TrainingGroupResponse;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
    private TrainingDeletionService trainingDeletionService;

    @MockitoBean
    private TrainingCompletionService trainingCompletionService;

    @MockitoBean
    private FreeTextTrainingService freeTextTrainingService;

    @Test
    void distinguishesOmittedEquipmentFromExplicitNullInFreeTextUpdates() throws Exception {
        when(freeTextTrainingService.update(org.mockito.ArgumentMatchers.eq(43L), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new UpdateTrainingResponse(43));
        String base = """
                {"name":"Session","assignmentType":"GROUP","trainingGroupId":3,"athleteIds":[],
                 "sections":[{"title":"Warmup","content":"Run","sequenceNumber":1}]%s}
                """;
        for (String equipment : List.of("", ",\"equipment\":null", ",\"equipment\":\"Ball\\nBands\"")) {
            mockMvc.perform(MockMvcRequestBuilders.put("/api/trainings/43/free-text")
                            .contentType(MediaType.APPLICATION_JSON).content(base.formatted(equipment)))
                    .andExpect(status().isOk());
        }
        var requests = org.mockito.ArgumentCaptor.forClass(EditFreeTextTrainingRequest.class);
        verify(freeTextTrainingService, org.mockito.Mockito.times(3)).update(
                org.mockito.ArgumentMatchers.eq(43L), requests.capture());
        assertNull(requests.getAllValues().get(0).equipment());
        assertTrue(requests.getAllValues().get(1).equipment().isNull());
        org.junit.jupiter.api.Assertions.assertEquals("Ball\nBands",
                requests.getAllValues().get(2).equipment().textValue());
    }

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
                                    ]
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
    void createsFreeTextTrainingThroughItsDedicatedEndpoint() throws Exception {
        when(freeTextTrainingService.create(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new CreateTrainingResponse(43));

        mockMvc.perform(MockMvcRequestBuilders.post("/api/trainings/free-text")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Trening ogólny",
                                  "trainingDate": "2026-11-10",
                                  "assignmentType": "GROUP",
                                  "trainingGroupId": 3,
                                  "athleteIds": [],
                                  "sections": [
                                    {"title": "Rozgrzewka", "content": "Trucht 10 minut", "sequenceNumber": 1}
                                  ]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(MockMvcResultMatchers.header().string("Location", "/api/trainings/43"))
                .andExpect(content().json("{\"id\":43}", true));
    }

    @Test
    void rejectsFreeTextTrainingWithoutSections() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/api/trainings/free-text")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Trening ogólny",
                                  "trainingDate": "2026-11-10",
                                  "assignmentType": "GROUP",
                                  "trainingGroupId": 3,
                                  "athleteIds": [],
                                  "sections": []
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void marksFreeTextTrainingCompletedWithoutUsingParticipationEndpoint() throws Exception {
        when(freeTextTrainingService.markCompleted(43)).thenReturn(new UpdateTrainingResponse(43));

        mockMvc.perform(MockMvcRequestBuilders.patch("/api/trainings/43/complete"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"id\":43}", true));

        verify(freeTextTrainingService).markCompleted(43);
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
                                    }]
                                  }]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"id\":42}", true));

        verify(trainingUpdateService).updateTraining(
                org.mockito.ArgumentMatchers.eq(42L), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void completesTrainingUsingExplicitParticipationOperations() throws Exception {
        when(trainingCompletionService.completeTraining(
                org.mockito.ArgumentMatchers.eq(42L), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new UpdateTrainingResponse(42));

        mockMvc.perform(MockMvcRequestBuilders.patch("/api/trainings/42/participations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "add": [{"definitionId": 5, "athleteId": 11, "executionUpdates": []}],
                                  "update": [],
                                  "removeParticipationIds": []
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"id\":42}", true));

        verify(trainingCompletionService).completeTraining(
                org.mockito.ArgumentMatchers.eq(42L), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void returnsTrainingDetailsFromTheService() throws Exception {
        when(trainingDetailsService.getTraining(42)).thenReturn(
                new TrainingDetailsResponse(
                        42,
                        "Intervals",
                        LocalDate.parse("2026-11-10"),
                        TrainingStatus.PLANNED,
                        new TrainingGroupResponse(3, "Group A"),
                        TrainingAssignmentType.GROUP,
                        List.of(),
                        List.of(),
                        TrainingType.TEMPO,
                        List.of()));

        mockMvc.perform(get("/api/trainings/42"))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {
                          "id": 42,
                          "name": "Intervals",
                          "trainingDate": "2026-11-10",
                          "status": "PLANNED",
                          "trainingGroup": {"id": 3, "name": "Group A"},
                          "assignmentType": "GROUP",
                          "assignedAthletes": [],
                          "definitions": [],
                          "trainingType": "TEMPO",
                          "sections": []
                        }
                        """, true));

        verify(trainingDetailsService).getTraining(42);
    }

    @Test
    void deletesTrainingAndReturnsNoContent() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.delete("/api/trainings/42"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(trainingDeletionService).deleteTraining(42);
    }

    @Test
    void returnsTrainingSummariesFromTheService() throws Exception {
        when(trainingQueryService.listTrainings(false, null)).thenReturn(List.of(
                new TrainingListItemResponse(7, "Intervals", LocalDate.parse("2026-11-10"),
                        new TrainingGroupResponse(3, "Group A"), 2, 12,
                        TrainingStatus.PLANNED, TrainingType.TEMPO, TrainingAssignmentType.GROUP, 0)));

        mockMvc.perform(get("/api/trainings"))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        [{
                          "id": 7,
                          "name": "Intervals",
                          "trainingDate": "2026-11-10",
                          "trainingGroup": {"id": 3, "name": "Group A"},
                          "definitionCount": 2,
                          "participantCount": 12,
                          "status": "PLANNED",
                          "trainingType": "TEMPO",
                          "assignmentType": "GROUP",
                          "assignedAthleteCount": 0
                        }]
                        """, true));

        verify(trainingQueryService).listTrainings(false, null);
    }

    @Test
    void includesCompletedTrainingsWhenRequested() throws Exception {
        when(trainingQueryService.listTrainings(true, null)).thenReturn(List.of(
                new TrainingListItemResponse(8, "Archived", LocalDate.parse("2026-11-09"),
                        new TrainingGroupResponse(3, "Group A"), 1, 4,
                        TrainingStatus.COMPLETED, TrainingType.TEMPO, TrainingAssignmentType.GROUP, 0)));

        mockMvc.perform(get("/api/trainings").param("includeCompleted", "true"))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        [{
                          "id": 8,
                          "name": "Archived",
                          "trainingDate": "2026-11-09",
                          "trainingGroup": {"id": 3, "name": "Group A"},
                          "definitionCount": 1,
                          "participantCount": 4,
                          "status": "COMPLETED",
                          "trainingType": "TEMPO",
                          "assignmentType": "GROUP",
                          "assignedAthleteCount": 0
                        }]
                        """, true));

        verify(trainingQueryService).listTrainings(true, null);
    }

    @Test
    void filtersTrainingsByTypeWhenRequested() throws Exception {
        when(trainingQueryService.listTrainings(true, TrainingType.FREE_TEXT)).thenReturn(List.of());

        mockMvc.perform(get("/api/trainings")
                        .param("includeCompleted", "true")
                        .param("trainingType", "FREE_TEXT"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]", true));

        verify(trainingQueryService).listTrainings(true, TrainingType.FREE_TEXT);
    }

    @Test
    void rejectsUnknownTrainingTypeFilter() throws Exception {
        mockMvc.perform(get("/api/trainings").param("trainingType", "UNKNOWN"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returnsAnEmptyCollectionWhenNoTrainingsAreVisible() throws Exception {
        when(trainingQueryService.listTrainings(false, null)).thenReturn(List.of());

        mockMvc.perform(get("/api/trainings"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]", true));
    }
}

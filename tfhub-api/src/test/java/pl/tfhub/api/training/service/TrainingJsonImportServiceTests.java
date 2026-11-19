package pl.tfhub.api.training.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import pl.tfhub.api.athlete.service.AthleteService;
import pl.tfhub.api.common.authorization.AccessScopeService;
import pl.tfhub.api.training.dto.request.CreateFreeTextTrainingRequest;
import pl.tfhub.api.training.dto.request.CreateTrainingRequest;
import pl.tfhub.api.training.dto.request.TrainingJsonImportItemRequest;
import pl.tfhub.api.training.dto.request.TrainingJsonImportRequest;
import pl.tfhub.api.training.dto.response.CreateTrainingResponse;
import pl.tfhub.api.training.enums.TrainingAssignmentType;
import pl.tfhub.api.training.exception.InvalidTrainingRequestException;
import pl.tfhub.api.training.repository.TrainingAssignmentRepository;
import pl.tfhub.api.training.repository.TrainingDefinitionRepository;
import pl.tfhub.api.training.repository.TrainingRepository;
import pl.tfhub.api.training.repository.TrainingSectionRepository;
import pl.tfhub.api.training.repository.TrainingSegmentRepository;
import tools.jackson.databind.ObjectMapper;

class TrainingJsonImportServiceTests {
    private final TrainingJsonTransferService transfer = new TrainingJsonTransferService(
            new ObjectMapper(),
            mock(TrainingRepository.class),
            mock(TrainingDefinitionRepository.class),
            mock(TrainingSegmentRepository.class),
            mock(TrainingSectionRepository.class),
            mock(TrainingAssignmentRepository.class),
            mock(AccessScopeService.class));
    private final TrainingCreationService tempoCreation = mock(TrainingCreationService.class);
    private final FreeTextTrainingService freeTextCreation = mock(FreeTextTrainingService.class);
    private final AccessScopeService access = mock(AccessScopeService.class);
    private final AthleteService athleteService = mock(AthleteService.class);
    private final TrainingJsonImportService service = new TrainingJsonImportService(
            transfer, tempoCreation, freeTextCreation, access, athleteService);

    @Test
    void createsSelectedTempoAndAthleteAssignedFreeTextAsNewTrainings() {
        String json = """
                {"formatVersion":1,"trainings":[
                  {"name":"Intervals","trainingType":"TEMPO","definitions":[
                    {"name":"Main","segments":[
                      {"distanceM":400,"restAfterSeconds":30},
                      {"distanceM":200,"restAfterSeconds":null}
                    ]}
                  ]},
                  {"name":"Mobility","trainingType":"FREE_TEXT","sections":[
                    {"title":"Warm-up","content":"Move"},
                    {"title":"Main","content":"Stretch"}
                  ],"equipment":"Bands"}
                ]}
                """;
        when(tempoCreation.createTraining(any())).thenReturn(new CreateTrainingResponse(101));
        when(freeTextCreation.createFromJsonImport(any())).thenReturn(new CreateTrainingResponse(102));
        var metadata = new TrainingJsonImportRequest(1, List.of(
                new TrainingJsonImportItemRequest(true, null, TrainingAssignmentType.GROUP, 4L, List.of()),
                new TrainingJsonImportItemRequest(true, LocalDate.parse("2026-03-10"),
                        TrainingAssignmentType.ATHLETES, null, List.of(20L, 21L))));

        var response = service.importFile(file(json), metadata);

        assertEquals(List.of(101L, 102L), response.createdTrainingIds());
        assertEquals(2, response.createdCount());
        var tempoCaptor = org.mockito.ArgumentCaptor.forClass(CreateTrainingRequest.class);
        verify(tempoCreation).createTraining(tempoCaptor.capture());
        CreateTrainingRequest tempoRequest = tempoCaptor.getValue();
        assertNull(tempoRequest.trainingDate());
        assertEquals(4L, tempoRequest.trainingGroupId());
        assertEquals(1, tempoRequest.definitions().get(0).sequenceNumber());
        assertEquals(2, tempoRequest.definitions().get(0).segments().size());
        assertEquals(2, tempoRequest.definitions().get(0).segments().get(1).sequenceNumber());

        var freeTextCaptor = org.mockito.ArgumentCaptor.forClass(CreateFreeTextTrainingRequest.class);
        verify(freeTextCreation).createFromJsonImport(freeTextCaptor.capture());
        CreateFreeTextTrainingRequest freeTextRequest = freeTextCaptor.getValue();
        assertEquals(LocalDate.parse("2026-03-10"), freeTextRequest.trainingDate());
        assertEquals(TrainingAssignmentType.ATHLETES, freeTextRequest.assignmentType());
        assertNull(freeTextRequest.trainingGroupId());
        assertEquals(List.of(20L, 21L), freeTextRequest.athleteIds());
        assertEquals("Bands", freeTextRequest.equipment());
        assertEquals(2, freeTextRequest.sections().size());
        assertEquals(2, freeTextRequest.sections().get(1).sequenceNumber());
        verify(athleteService).validateActiveTrainingAssignees(List.of(20L, 21L));
    }

    @Test
    void deselectedEntriesAreNotCreatedButTheirFileContentIsStillValidated() {
        String json = """
                {"formatVersion":1,"trainings":[
                  {"name":"Keep","trainingType":"TEMPO","definitions":[
                    {"name":"Main","segments":[{"distanceM":300}]}
                  ]},
                  {"name":"Skip","trainingType":"FREE_TEXT","sections":[
                    {"title":"Section","content":"Text"}
                  ]}
                ]}
                """;
        when(tempoCreation.createTraining(any())).thenReturn(new CreateTrainingResponse(201));
        var request = new TrainingJsonImportRequest(1, List.of(
                new TrainingJsonImportItemRequest(true, null, TrainingAssignmentType.GROUP, 4L, List.of()),
                new TrainingJsonImportItemRequest(false, null, null, null, null)));

        var response = service.importFile(file(json), request);

        assertEquals(List.of(201L), response.createdTrainingIds());
        verify(tempoCreation).createTraining(any());
        verifyNoInteractions(freeTextCreation);
    }

    @Test
    void rejectsAnyInvalidTrainingEvenWhenDeselected() {
        String json = """
                {"formatVersion":1,"trainings":[
                  {"name":"Keep","trainingType":"TEMPO","definitions":[
                    {"name":"Main","segments":[{"distanceM":300}]}
                  ]},
                  {"name":"Invalid","trainingType":"FREE_TEXT","sections":[]}
                ]}
                """;
        var request = new TrainingJsonImportRequest(1, List.of(
                new TrainingJsonImportItemRequest(true, null, TrainingAssignmentType.GROUP, 4L, List.of()),
                new TrainingJsonImportItemRequest(false, null, null, null, null)));

        assertThrows(InvalidTrainingRequestException.class, () -> service.importFile(file(json), request));
        verifyNoInteractions(tempoCreation, freeTextCreation);
    }

    @Test
    void validatesAllSelectedAssignmentsBeforeCreatingAnyTraining() {
        String json = """
                {"formatVersion":1,"trainings":[
                  {"name":"Keep","trainingType":"TEMPO","definitions":[
                    {"name":"Main","segments":[{"distanceM":300}]}
                  ]},
                  {"name":"No athlete","trainingType":"FREE_TEXT","sections":[
                    {"title":"Section","content":"Text"}
                  ]}
                ]}
                """;
        var request = new TrainingJsonImportRequest(1, List.of(
                new TrainingJsonImportItemRequest(true, null, TrainingAssignmentType.GROUP, 4L, List.of()),
                new TrainingJsonImportItemRequest(true, null, TrainingAssignmentType.ATHLETES, null, List.of())));

        assertThrows(InvalidTrainingRequestException.class, () -> service.importFile(file(json), request));
        verifyNoInteractions(tempoCreation, freeTextCreation);
    }

    @Test
    void rejectsUnauthorizedCoachGroupBeforeCreatingAnything() {
        String json = """
                {"formatVersion":1,"trainings":[
                  {"name":"Session","trainingType":"TEMPO","definitions":[
                    {"name":"Main","segments":[{"distanceM":300}]}
                  ]}
                ]}
                """;
        doThrow(new AccessDeniedException("outside scope")).when(access).requireGroupAssignment(99L);
        var request = new TrainingJsonImportRequest(1, List.of(
                new TrainingJsonImportItemRequest(true, null, TrainingAssignmentType.GROUP, 99L, List.of())));

        assertThrows(AccessDeniedException.class, () -> service.importFile(file(json), request));
        verifyNoInteractions(tempoCreation, freeTextCreation);
    }

    @Test
    void rejectsAthleteBeforeReadingOrCreatingAnImport() {
        doThrow(new AccessDeniedException("Only coaches and administrators may import trainings"))
                .when(access).requireCoachOrAdmin();
        var request = new TrainingJsonImportRequest(1, List.of(
                new TrainingJsonImportItemRequest(true, null, TrainingAssignmentType.GROUP, 4L, List.of())));

        assertThrows(AccessDeniedException.class, () -> service.importFile(file("{}"), request));

        verifyNoInteractions(tempoCreation, freeTextCreation);
    }

    @Test
    void rejectsCoachAssignmentToAnUnauthorizedAthleteBeforeCreation() {
        String json = """
                {"formatVersion":1,"trainings":[
                  {"name":"Mobility","trainingType":"FREE_TEXT","sections":[
                    {"title":"Section","content":"Move"}
                  ]}
                ]}
                """;
        doThrow(new AccessDeniedException("outside assigned groups"))
                .when(athleteService).validateActiveTrainingAssignees(List.of(20L));
        var request = new TrainingJsonImportRequest(1, List.of(
                new TrainingJsonImportItemRequest(
                        true, null, TrainingAssignmentType.ATHLETES, null, List.of(20L))));

        assertThrows(AccessDeniedException.class, () -> service.importFile(file(json), request));

        verifyNoInteractions(tempoCreation, freeTextCreation);
    }

    private MockMultipartFile file(String content) {
        return new MockMultipartFile("file", "trainings.json", "application/json",
                content.getBytes(StandardCharsets.UTF_8));
    }
}

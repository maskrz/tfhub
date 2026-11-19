package pl.tfhub.api.training.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import pl.tfhub.api.common.authorization.AccessScopeService;
import pl.tfhub.api.training.entity.Training;
import pl.tfhub.api.training.entity.TrainingDefinition;
import pl.tfhub.api.training.entity.TrainingSection;
import pl.tfhub.api.training.entity.TrainingSegment;
import pl.tfhub.api.training.enums.TrainingType;
import pl.tfhub.api.training.exception.InvalidTrainingRequestException;
import pl.tfhub.api.training.exception.TrainingNotFoundException;
import pl.tfhub.api.training.repository.TrainingAssignmentRepository;
import pl.tfhub.api.training.repository.TrainingDefinitionRepository;
import pl.tfhub.api.training.repository.TrainingRepository;
import pl.tfhub.api.training.repository.TrainingSectionRepository;
import pl.tfhub.api.training.repository.TrainingSegmentRepository;
import pl.tfhub.api.user.enums.UserRole;
import pl.tfhub.api.user.service.AuthenticatedUser;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class TrainingJsonTransferServiceTests {
    private final TrainingRepository trainings = mock(TrainingRepository.class);
    private final TrainingDefinitionRepository definitions = mock(TrainingDefinitionRepository.class);
    private final TrainingSegmentRepository segments = mock(TrainingSegmentRepository.class);
    private final TrainingSectionRepository sections = mock(TrainingSectionRepository.class);
    private final TrainingAssignmentRepository assignments = mock(TrainingAssignmentRepository.class);
    private final AccessScopeService access = mock(AccessScopeService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final TrainingJsonTransferService service = new TrainingJsonTransferService(
            objectMapper, trainings, definitions, segments, sections, assignments, access);

    @Test
    void exportsTempoAndFreeTextDefinitionsWithoutEnvironmentOrExecutionData() throws Exception {
        Training tempo = training(1, "Intervals", TrainingType.TEMPO);
        Training freeText = training(2, "Mobility", TrainingType.FREE_TEXT);
        when(access.currentUser()).thenReturn(user(UserRole.ADMIN));
        when(trainings.findAllByIdIn(List.of(1L, 2L))).thenReturn(List.of(tempo, freeText));

        TrainingDefinition definition = mock(TrainingDefinition.class);
        when(definition.getTraining()).thenReturn(tempo);
        when(definition.getId()).thenReturn(21L);
        when(definition.getName()).thenReturn("Variant A");
        when(definitions.findAllByTraining_IdInOrderByTraining_IdAscSequenceNumberAsc(List.of(1L)))
                .thenReturn(List.of(definition));
        TrainingSegment segment = mock(TrainingSegment.class);
        when(segment.getTrainingDefinition()).thenReturn(definition);
        when(segment.getDistanceM()).thenReturn(400);
        when(segment.getRestAfterSeconds()).thenReturn(60);
        when(segments.findAllByTrainingDefinition_IdInOrderByTrainingDefinition_IdAscSequenceNumberAsc(List.of(21L)))
                .thenReturn(List.of(segment));

        TrainingSection section = mock(TrainingSection.class);
        when(section.getTraining()).thenReturn(freeText);
        when(section.getTitle()).thenReturn("Warm-up");
        when(section.getContent()).thenReturn("Run and mobility drills");
        when(sections.findAllByTraining_IdInOrderByTraining_IdAscSequenceNumberAsc(List.of(2L)))
                .thenReturn(List.of(section));

        JsonNode export = objectMapper.readTree(service.export(new pl.tfhub.api.training.dto.request.TrainingIdsRequest(
                List.of(1L, 2L))));
        assertEquals(1, export.get("formatVersion").intValue());
        JsonNode exportedTempo = export.get("trainings").get(0);
        assertEquals("Intervals", exportedTempo.get("name").textValue());
        assertEquals("TEMPO", exportedTempo.get("trainingType").textValue());
        assertEquals("Variant A", exportedTempo.get("definitions").get(0).get("name").textValue());
        assertEquals(400, exportedTempo.get("definitions").get(0).get("segments").get(0)
                .get("distanceM").intValue());
        assertEquals(60, exportedTempo.get("definitions").get(0).get("segments").get(0)
                .get("restAfterSeconds").intValue());

        JsonNode exportedFreeText = export.get("trainings").get(1);
        assertEquals("FREE_TEXT", exportedFreeText.get("trainingType").textValue());
        assertEquals("Warm-up", exportedFreeText.get("sections").get(0).get("title").textValue());
        assertEquals("Run and mobility drills", exportedFreeText.get("sections").get(0).get("content").textValue());
        assertEquals("Bands", exportedFreeText.get("equipment").textValue());
        for (JsonNode item : export.get("trainings")) {
            assertFalse(item.has("id"));
            assertFalse(item.has("trainingDate"));
            assertFalse(item.has("status"));
            assertFalse(item.has("trainingGroup"));
            assertFalse(item.has("athleteIds"));
            assertFalse(item.has("assignedAthletes"));
            assertFalse(item.has("participations"));
            assertFalse(item.has("executions"));
            assertFalse(item.has("results"));
        }
        verifyNoInteractions(assignments);
    }

    @Test
    void coachCannotExportAnyTrainingOutsideCurrentAccess() {
        Training training = training(5, "Session", TrainingType.TEMPO);
        when(access.currentUser()).thenReturn(user(UserRole.COACH));
        when(trainings.findAllByIdIn(List.of(5L))).thenReturn(List.of(training));
        when(assignments.canCoachManage(5, 7)).thenReturn(false);

        assertThrows(TrainingNotFoundException.class, () -> service.export(
                new pl.tfhub.api.training.dto.request.TrainingIdsRequest(List.of(5L))));
        verifyNoInteractions(definitions, segments, sections);
    }

    @Test
    void athleteCannotExportTrainings() {
        when(access.currentUser()).thenReturn(user(UserRole.ATHLETE));

        assertThrows(AccessDeniedException.class, () -> service.export(
                new pl.tfhub.api.training.dto.request.TrainingIdsRequest(List.of(5L))));
        verifyNoInteractions(trainings, definitions, segments, sections, assignments);
    }

    @Test
    void validatesVersionedFileAndPreservesOrderedContent() {
        var result = service.parseFile(file("""
                {
                  "formatVersion": 1,
                  "trainings": [
                    {"name":"Intervals","trainingType":"TEMPO","definitions":[
                      {"name":"Variant","segments":[
                        {"distanceM":400,"restAfterSeconds":30},
                        {"distanceM":200,"restAfterSeconds":null}
                      ]}
                    ]},
                    {"name":"Mobility","trainingType":"FREE_TEXT","sections":[
                      {"title":"First","content":"Content one"},
                      {"title":"Second","content":"Content two"}
                    ],"equipment":"Bands"}
                  ]
                }
                """));

        assertEquals(2, result.trainings().size());
        assertEquals("Variant", result.trainings().get(0).definitions().get(0).name());
        assertEquals(200, result.trainings().get(0).definitions().get(0).segments().get(1).distanceM());
        assertEquals("Second", result.trainings().get(1).sections().get(1).title());
        assertEquals("Bands", result.trainings().get(1).equipment());
    }

    @Test
    void rejectsMalformedUnsupportedDuplicateUnknownAndStructurallyInvalidDocuments() {
        for (String invalid : List.of(
                "{",
                "{\"formatVersion\":2,\"trainings\":[]}",
                "{\"formatVersion\":1,\"formatVersion\":1,\"trainings\":[]}",
                "{\"formatVersion\":1,\"trainings\":[{\"name\":\"x\",\"trainingType\":\"TEMPO\","
                        + "\"trainingDate\":\"2025-01-01\",\"definitions\":[]}] }",
                "{\"formatVersion\":1,\"trainings\":[{\"name\":\"x\",\"trainingType\":\"TEMPO\","
                        + "\"definitions\":[{\"name\":\"v\",\"segments\":[{\"distanceM\":0}]}]}]}",
                "{\"formatVersion\":1,\"trainings\":[{\"name\":\"x\",\"trainingType\":\"FREE_TEXT\","
                        + "\"sections\":[{\"title\":\" \",\"content\":\"ok\"}]}]}",
                "{\"formatVersion\":1,\"trainings\":[]} {}")) {
            assertThrows(InvalidTrainingRequestException.class, () -> service.parseFile(file(invalid)), invalid);
        }
    }

    @Test
    void rejectsEntireFileWhenASecondTrainingIsInvalid() {
        String invalidBatch = """
                {"formatVersion":1,"trainings":[
                  {"name":"Valid","trainingType":"TEMPO","definitions":[
                    {"name":"Main","segments":[{"distanceM":400}]}
                  ]},
                  {"name":"Invalid","trainingType":"TEMPO","definitions":[]}
                ]}
                """;
        assertThrows(InvalidTrainingRequestException.class, () -> service.parseFile(file(invalidBatch)));
        verifyNoInteractions(trainings, definitions, segments, sections, assignments);
    }

    @Test
    void enforcesFileSizeAndTrainingCountLimits() {
        byte[] oversized = new byte[(int) TrainingJsonTransferService.MAX_FILE_BYTES + 1];
        assertThrows(InvalidTrainingRequestException.class, () -> service.parseFile(
                new MockMultipartFile("file", "large.json", "application/json", oversized)));

        StringBuilder json = new StringBuilder("{\"formatVersion\":1,\"trainings\":[");
        for (int index = 0; index <= TrainingJsonTransferService.MAX_TRAININGS; index++) {
            if (index > 0) json.append(',');
            json.append("{\"name\":\"x\",\"trainingType\":\"TEMPO\",\"definitions\":[")
                    .append("{\"name\":\"v\",\"segments\":[{\"distanceM\":1}]}]}");
        }
        json.append("]}");
        assertThrows(InvalidTrainingRequestException.class, () -> service.parseFile(file(json.toString())));
    }

    @Test
    void coachAndAdminCanValidateButAthleteCannot() {
        doNothing().when(access).requireCoachOrAdmin();
        service.validateFile(file("""
                {"formatVersion":1,"trainings":[
                  {"name":"x","trainingType":"TEMPO","definitions":[
                    {"name":"v","segments":[{"distanceM":1}]}
                  ]}
                ]}
                """));
        verify(access).requireCoachOrAdmin();
    }

    private MockMultipartFile file(String content) {
        return new MockMultipartFile("file", "trainings.json", "application/json",
                content.getBytes(StandardCharsets.UTF_8));
    }

    private Training training(long id, String name, TrainingType type) {
        Training training = mock(Training.class);
        when(training.getId()).thenReturn(id);
        when(training.getName()).thenReturn(name);
        when(training.getTrainingType()).thenReturn(type);
        when(training.getEquipment()).thenReturn(type == TrainingType.FREE_TEXT ? "Bands" : null);
        return training;
    }

    private AuthenticatedUser user(UserRole role) {
        return new AuthenticatedUser(7, "user", "", role, true);
    }
}

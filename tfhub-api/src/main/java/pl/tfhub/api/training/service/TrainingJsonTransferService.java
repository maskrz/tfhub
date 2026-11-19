package pl.tfhub.api.training.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import pl.tfhub.api.common.authorization.AccessScopeService;
import pl.tfhub.api.training.dto.request.TrainingIdsRequest;
import pl.tfhub.api.training.dto.response.TrainingJsonDocumentResponse;
import pl.tfhub.api.training.entity.Training;
import pl.tfhub.api.training.entity.TrainingDefinition;
import pl.tfhub.api.training.enums.TrainingType;
import pl.tfhub.api.training.exception.InvalidTrainingRequestException;
import pl.tfhub.api.training.exception.TrainingNotFoundException;
import pl.tfhub.api.training.repository.TrainingAssignmentRepository;
import pl.tfhub.api.training.repository.TrainingDefinitionRepository;
import pl.tfhub.api.training.repository.TrainingRepository;
import pl.tfhub.api.training.repository.TrainingSectionRepository;
import pl.tfhub.api.training.repository.TrainingSegmentRepository;
import pl.tfhub.api.user.enums.UserRole;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@Service
public class TrainingJsonTransferService {

    public static final int FORMAT_VERSION = 1;
    public static final long MAX_FILE_BYTES = 1024L * 1024L;
    public static final int MAX_TRAININGS = 100;
    public static final int MAX_DEFINITIONS_PER_TRAINING = 50;
    public static final int MAX_SEGMENTS_PER_DEFINITION = 100;
    public static final int MAX_SECTIONS_PER_TRAINING = 100;

    private static final JsonFactory STRICT_JSON_FACTORY = JsonFactory.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .build();
    private static final Set<String> ROOT_FIELDS = Set.of("formatVersion", "trainings");
    private static final Set<String> TEMPO_FIELDS = Set.of("name", "trainingType", "definitions");
    private static final Set<String> FREE_TEXT_FIELDS =
            Set.of("name", "trainingType", "sections", "equipment");
    private static final Set<String> DEFINITION_FIELDS = Set.of("name", "segments");
    private static final Set<String> SEGMENT_FIELDS = Set.of("distanceM", "restAfterSeconds");
    private static final Set<String> SECTION_FIELDS = Set.of("title", "content");

    private final ObjectMapper objectMapper;
    private final TrainingRepository trainingRepository;
    private final TrainingDefinitionRepository definitionRepository;
    private final TrainingSegmentRepository segmentRepository;
    private final TrainingSectionRepository sectionRepository;
    private final TrainingAssignmentRepository assignmentRepository;
    private final AccessScopeService accessScopeService;

    public TrainingJsonTransferService(
            ObjectMapper objectMapper,
            TrainingRepository trainingRepository,
            TrainingDefinitionRepository definitionRepository,
            TrainingSegmentRepository segmentRepository,
            TrainingSectionRepository sectionRepository,
            TrainingAssignmentRepository assignmentRepository,
            AccessScopeService accessScopeService) {
        this.objectMapper = objectMapper;
        this.trainingRepository = trainingRepository;
        this.definitionRepository = definitionRepository;
        this.segmentRepository = segmentRepository;
        this.sectionRepository = sectionRepository;
        this.assignmentRepository = assignmentRepository;
        this.accessScopeService = accessScopeService;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public byte[] export(TrainingIdsRequest request) {
        List<Long> ids = TrainingBulkService.validatedIds(request.trainingIds());
        var user = accessScopeService.currentUser();
        if (user.role() != UserRole.ADMIN && user.role() != UserRole.COACH) {
            throw new AccessDeniedException("Only coaches and administrators can export trainings");
        }

        Map<Long, Training> trainingsById = new HashMap<>();
        trainingRepository.findAllByIdIn(ids).forEach(training -> trainingsById.put(training.getId(), training));
        for (Long id : ids) {
            Training training = trainingsById.get(id);
            if (training == null) {
                throw new TrainingNotFoundException(id);
            }
            if (user.role() == UserRole.COACH
                    && !assignmentRepository.canCoachManage(id, user.id())) {
                throw new TrainingNotFoundException(id);
            }
        }

        List<Long> tempoIds = ids.stream()
                .filter(id -> trainingsById.get(id).getTrainingType() == TrainingType.TEMPO)
                .toList();
        List<TrainingDefinition> definitions = tempoIds.isEmpty()
                ? List.of()
                : definitionRepository.findAllByTraining_IdInOrderByTraining_IdAscSequenceNumberAsc(tempoIds);
        Map<Long, List<TrainingDefinition>> definitionsByTraining = definitions.stream()
                .collect(Collectors.groupingBy(definition -> definition.getTraining().getId()));
        List<Long> definitionIds = definitions.stream().map(TrainingDefinition::getId).toList();
        var segments = definitionIds.isEmpty()
                ? List.<pl.tfhub.api.training.entity.TrainingSegment>of()
                : segmentRepository.findAllByTrainingDefinition_IdInOrderByTrainingDefinition_IdAscSequenceNumberAsc(
                        definitionIds);
        Map<Long, List<pl.tfhub.api.training.entity.TrainingSegment>> segmentsByDefinition = segments.stream()
                .collect(Collectors.groupingBy(segment -> segment.getTrainingDefinition().getId()));
        List<Long> freeTextIds = ids.stream()
                .filter(id -> trainingsById.get(id).getTrainingType() == TrainingType.FREE_TEXT).toList();
        var sections = freeTextIds.isEmpty()
                ? List.<pl.tfhub.api.training.entity.TrainingSection>of()
                : sectionRepository.findAllByTraining_IdInOrderByTraining_IdAscSequenceNumberAsc(freeTextIds);
        Map<Long, List<pl.tfhub.api.training.entity.TrainingSection>> sectionsByTraining = sections.stream()
                .collect(Collectors.groupingBy(section -> section.getTraining().getId()));

        ObjectNode document = objectMapper.createObjectNode();
        document.put("formatVersion", FORMAT_VERSION);
        ArrayNode trainingNodes = document.putArray("trainings");
        for (Long id : ids) {
            Training training = trainingsById.get(id);
            ObjectNode trainingNode = trainingNodes.addObject();
            trainingNode.put("name", training.getName());
            trainingNode.put("trainingType", training.getTrainingType().name());
            if (training.getTrainingType() == TrainingType.TEMPO) {
                ArrayNode definitionNodes = trainingNode.putArray("definitions");
                for (TrainingDefinition definition : definitionsByTraining.getOrDefault(id, List.of())) {
                    ObjectNode definitionNode = definitionNodes.addObject();
                    definitionNode.put("name", definition.getName());
                    ArrayNode segmentNodes = definitionNode.putArray("segments");
                    for (var segment : segmentsByDefinition.getOrDefault(definition.getId(), List.of())) {
                        ObjectNode segmentNode = segmentNodes.addObject();
                        segmentNode.put("distanceM", segment.getDistanceM());
                        if (segment.getRestAfterSeconds() == null) {
                            segmentNode.putNull("restAfterSeconds");
                        } else {
                            segmentNode.put("restAfterSeconds", segment.getRestAfterSeconds());
                        }
                    }
                }
            } else {
                ArrayNode sectionNodes = trainingNode.putArray("sections");
                for (var section : sectionsByTraining.getOrDefault(id, List.of())) {
                    ObjectNode sectionNode = sectionNodes.addObject();
                    sectionNode.put("title", section.getTitle());
                    sectionNode.put("content", section.getContent());
                }
                if (training.getEquipment() == null) {
                    trainingNode.putNull("equipment");
                } else {
                    trainingNode.put("equipment", training.getEquipment());
                }
            }
        }
        try {
            return objectMapper.writeValueAsBytes(document);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Unable to serialize training export", exception);
        }
    }

    public TrainingJsonDocumentResponse validateFile(MultipartFile file) {
        accessScopeService.requireCoachOrAdmin();
        ValidatedDocument validated = parseFile(file);
        return new TrainingJsonDocumentResponse(FORMAT_VERSION,
                validated.trainings().stream().map(ValidatedTraining::json).toList());
    }

    public ValidatedDocument parseFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw invalid("Plik JSON jest pusty.");
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            throw invalid("Plik JSON przekracza limit 1 MiB.");
        }
        final byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException exception) {
            throw invalid("Nie udało się odczytać pliku JSON.");
        }
        if (bytes.length > MAX_FILE_BYTES) {
            throw invalid("Plik JSON przekracza limit 1 MiB.");
        }

        try (JsonParser parser = STRICT_JSON_FACTORY.createParser(bytes)) {
            JsonNode root = objectMapper.readTree(parser);
            if (root == null || parser.nextToken() != null) {
                throw invalid("Plik musi zawierać dokładnie jeden dokument JSON.");
            }
            return parseDocument(root);
        } catch (JacksonException exception) {
            throw invalid("Plik zawiera nieprawidłowy lub uszkodzony JSON.");
        }
    }

    public ValidatedDocument parseDocument(JsonNode root) {
        requireObject(root, "Plik");
        requireOnlyFields(root, ROOT_FIELDS, "Plik");
        int version = requiredInteger(root, "formatVersion", "formatVersion", 1, Integer.MAX_VALUE);
        if (version != FORMAT_VERSION) {
            throw invalid("Nieobsługiwana wersja formatu JSON: " + version + ".");
        }
        JsonNode trainingArray = required(root, "trainings", "trainings");
        if (!trainingArray.isArray() || trainingArray.isEmpty()) {
            throw invalid("Pole trainings musi zawierać co najmniej jeden trening.");
        }
        if (trainingArray.size() > MAX_TRAININGS) {
            throw invalid("Plik może zawierać najwyżej 100 treningów.");
        }

        List<ValidatedTraining> trainings = new ArrayList<>();
        ArrayNode normalizedTrainings = objectMapper.createArrayNode();
        for (int index = 0; index < trainingArray.size(); index++) {
            String path = "trainings[" + (index + 1) + "]";
            ValidatedTraining training = parseTraining(trainingArray.get(index), path);
            trainings.add(training);
            normalizedTrainings.add(training.json());
        }
        ObjectNode normalized = objectMapper.createObjectNode();
        normalized.put("formatVersion", FORMAT_VERSION);
        normalized.set("trainings", normalizedTrainings);
        return new ValidatedDocument(List.copyOf(trainings), normalized);
    }

    private ValidatedTraining parseTraining(JsonNode node, String path) {
        requireObject(node, path);
        String trainingType = requiredText(node, "trainingType", path + ".trainingType", 20);
        if (!trainingType.equals(TrainingType.TEMPO.name())
                && !trainingType.equals(TrainingType.FREE_TEXT.name())) {
            throw invalid(path + ".trainingType musi mieć wartość TEMPO lub FREE_TEXT.");
        }
        TrainingType type = TrainingType.valueOf(trainingType);
        requireOnlyFields(node, type == TrainingType.TEMPO ? TEMPO_FIELDS : FREE_TEXT_FIELDS, path);
        String name = requiredText(node, "name", path + ".name", 255);
        ObjectNode normalized = objectMapper.createObjectNode();
        normalized.put("name", name);
        normalized.put("trainingType", trainingType);

        if (type == TrainingType.TEMPO) {
            JsonNode definitionArray = required(node, "definitions", path + ".definitions");
            if (!definitionArray.isArray() || definitionArray.isEmpty()) {
                throw invalid(path + ".definitions musi zawierać co najmniej jedną definicję.");
            }
            if (definitionArray.size() > MAX_DEFINITIONS_PER_TRAINING) {
                throw invalid(path + ".definitions może zawierać najwyżej 50 pozycji.");
            }
            ArrayNode definitions = normalized.putArray("definitions");
            for (int definitionIndex = 0; definitionIndex < definitionArray.size(); definitionIndex++) {
                String definitionPath = path + ".definitions[" + (definitionIndex + 1) + "]";
                JsonNode definitionNode = definitionArray.get(definitionIndex);
                requireObject(definitionNode, definitionPath);
                requireOnlyFields(definitionNode, DEFINITION_FIELDS, definitionPath);
                ObjectNode normalizedDefinition = definitions.addObject();
                normalizedDefinition.put("name", requiredText(
                        definitionNode, "name", definitionPath + ".name", 255));
                JsonNode segmentArray = required(definitionNode, "segments", definitionPath + ".segments");
                if (!segmentArray.isArray() || segmentArray.isEmpty()) {
                    throw invalid(definitionPath + ".segments musi zawierać co najmniej jeden odcinek.");
                }
                if (segmentArray.size() > MAX_SEGMENTS_PER_DEFINITION) {
                    throw invalid(definitionPath + ".segments może zawierać najwyżej 100 pozycji.");
                }
                ArrayNode segments = normalizedDefinition.putArray("segments");
                for (int segmentIndex = 0; segmentIndex < segmentArray.size(); segmentIndex++) {
                    String segmentPath = definitionPath + ".segments[" + (segmentIndex + 1) + "]";
                    JsonNode segmentNode = segmentArray.get(segmentIndex);
                    requireObject(segmentNode, segmentPath);
                    requireOnlyFields(segmentNode, SEGMENT_FIELDS, segmentPath);
                    ObjectNode normalizedSegment = segments.addObject();
                    normalizedSegment.put("distanceM", requiredInteger(
                            segmentNode, "distanceM", segmentPath + ".distanceM", 1, Integer.MAX_VALUE));
                    Integer rest = optionalInteger(
                            segmentNode, "restAfterSeconds", segmentPath + ".restAfterSeconds", 0);
                    if (rest == null) {
                        normalizedSegment.putNull("restAfterSeconds");
                    } else {
                        normalizedSegment.put("restAfterSeconds", rest);
                    }
                }
            }
            return new ValidatedTraining(name, type, List.copyOf(parseTempoDefinitions(normalized)),
                    List.of(), null, normalized);
        }

        JsonNode sectionArray = required(node, "sections", path + ".sections");
        if (!sectionArray.isArray() || sectionArray.isEmpty()) {
            throw invalid(path + ".sections musi zawierać co najmniej jedną sekcję.");
        }
        if (sectionArray.size() > MAX_SECTIONS_PER_TRAINING) {
            throw invalid(path + ".sections może zawierać najwyżej 100 pozycji.");
        }
        ArrayNode sections = normalized.putArray("sections");
        List<FreeTextSection> parsedSections = new ArrayList<>();
        for (int sectionIndex = 0; sectionIndex < sectionArray.size(); sectionIndex++) {
            String sectionPath = path + ".sections[" + (sectionIndex + 1) + "]";
            JsonNode sectionNode = sectionArray.get(sectionIndex);
            requireObject(sectionNode, sectionPath);
            requireOnlyFields(sectionNode, SECTION_FIELDS, sectionPath);
            String title = requiredText(sectionNode, "title", sectionPath + ".title", Integer.MAX_VALUE);
            String content = requiredText(sectionNode, "content", sectionPath + ".content", Integer.MAX_VALUE);
            ObjectNode normalizedSection = sections.addObject();
            normalizedSection.put("title", title);
            normalizedSection.put("content", content);
            parsedSections.add(new FreeTextSection(title, content));
        }
        String equipment = optionalText(node, "equipment", path + ".equipment");
        if (equipment == null) {
            normalized.putNull("equipment");
        } else {
            normalized.put("equipment", equipment);
        }
        return new ValidatedTraining(name, type, List.of(), List.copyOf(parsedSections), equipment, normalized);
    }

    private List<TempoDefinition> parseTempoDefinitions(JsonNode training) {
        List<TempoDefinition> definitions = new ArrayList<>();
        JsonNode definitionArray = training.get("definitions");
        for (JsonNode definition : definitionArray) {
            List<TempoSegment> segments = new ArrayList<>();
            for (JsonNode segment : definition.get("segments")) {
                JsonNode rest = segment.get("restAfterSeconds");
                segments.add(new TempoSegment(segment.get("distanceM").intValue(),
                        rest == null || rest.isNull() ? null : rest.intValue()));
            }
            definitions.add(new TempoDefinition(definition.get("name").textValue(), List.copyOf(segments)));
        }
        return definitions;
    }

    private void requireObject(JsonNode node, String path) {
        if (node == null || !node.isObject()) {
            throw invalid(path + " musi być obiektem JSON.");
        }
    }

    private void requireOnlyFields(JsonNode node, Set<String> allowed, String path) {
        node.properties().forEach(property -> {
            String field = property.getKey();
            if (!allowed.contains(field)) {
                throw invalid(path + " zawiera nieobsługiwane pole: " + field + ".");
            }
        });
    }

    private JsonNode required(JsonNode object, String field, String path) {
        JsonNode value = object.get(field);
        if (value == null || value.isNull()) {
            throw invalid(path + " jest wymagane.");
        }
        return value;
    }

    private String requiredText(JsonNode object, String field, String path, int maxLength) {
        JsonNode value = required(object, field, path);
        if (!value.isTextual()) {
            throw invalid(path + " musi być tekstem.");
        }
        String text = value.textValue();
        if (text.isBlank()) {
            throw invalid(path + " nie może być puste.");
        }
        if (text.length() > maxLength) {
            throw invalid(path + " przekracza dozwoloną długość.");
        }
        return text;
    }

    private String optionalText(JsonNode object, String field, String path) {
        JsonNode value = object.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isTextual()) {
            throw invalid(path + " musi być tekstem lub null.");
        }
        return value.textValue();
    }

    private int requiredInteger(JsonNode object, String field, String path, int minimum, int maximum) {
        JsonNode value = required(object, field, path);
        if (!value.isIntegralNumber() || !value.canConvertToInt()) {
            throw invalid(path + " musi być liczbą całkowitą.");
        }
        int number = value.intValue();
        if (number < minimum || number > maximum) {
            throw invalid(path + " ma wartość poza dozwolonym zakresem.");
        }
        return number;
    }

    private Integer optionalInteger(JsonNode object, String field, String path, int minimum) {
        JsonNode value = object.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isIntegralNumber() || !value.canConvertToInt()) {
            throw invalid(path + " musi być liczbą całkowitą lub null.");
        }
        int number = value.intValue();
        if (number < minimum) {
            throw invalid(path + " ma wartość poza dozwolonym zakresem.");
        }
        return number;
    }

    private InvalidTrainingRequestException invalid(String message) {
        return new InvalidTrainingRequestException(message);
    }

    public record FreeTextSection(String title, String content) {
    }

    public record TempoSegment(int distanceM, Integer restAfterSeconds) {
    }

    public record TempoDefinition(String name, List<TempoSegment> segments) {
    }

    public record ValidatedTraining(
            String name,
            TrainingType type,
            List<TempoDefinition> definitions,
            List<FreeTextSection> sections,
            String equipment,
            JsonNode json) {
    }

    public record ValidatedDocument(List<ValidatedTraining> trainings, JsonNode json) {
    }
}

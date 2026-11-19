package pl.tfhub.api.training.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import pl.tfhub.api.common.authorization.AccessScopeService;
import pl.tfhub.api.training.entity.Training;
import pl.tfhub.api.training.enums.TrainingAssignmentType;
import pl.tfhub.api.training.enums.TrainingType;
import pl.tfhub.api.training.exception.TrainingNotFoundException;
import pl.tfhub.api.training.repository.TrainingAssignmentRepository;
import pl.tfhub.api.training.repository.TrainingDefinitionRepository;
import pl.tfhub.api.training.repository.TrainingRepository;
import pl.tfhub.api.training.repository.TrainingSectionRepository;
import pl.tfhub.api.training.repository.TrainingSegmentRepository;
import pl.tfhub.api.user.enums.UserRole;
import pl.tfhub.api.user.service.AuthenticatedUser;

class TrainingPrintServiceTests {
    private final TrainingRepository repository = mock(TrainingRepository.class);
    private final TrainingAssignmentRepository assignments = mock(TrainingAssignmentRepository.class);
    private final TrainingDefinitionRepository definitions = mock(TrainingDefinitionRepository.class);
    private final TrainingSegmentRepository segments = mock(TrainingSegmentRepository.class);
    private final TrainingSectionRepository sections = mock(TrainingSectionRepository.class);
    private final AccessScopeService access = mock(AccessScopeService.class);
    private final TrainingPrintService service = new TrainingPrintService(
            repository, assignments, definitions, segments, sections, access);

    @Test
    void athleteDoesNotReceiveEquipmentOrOtherAssignedAthletes() {
        Training training = training(1, TrainingType.FREE_TEXT);
        when(access.currentUser()).thenReturn(new AuthenticatedUser(1, "athlete", "", UserRole.ATHLETE, true));
        when(access.currentAthleteId()).thenReturn(7L);
        when(repository.findAllByIdIn(List.of(1L))).thenReturn(List.of(training));
        when(assignments.isVisibleToAthlete(1, 7)).thenReturn(true);
        var result = service.getPrintData(List.of(1L)).trainings().getFirst();
        assertNull(result.equipment());
        assertTrue(result.assignedAthletes().isEmpty());
        verify(assignments, never()).findAssignedAthletesForTrainings(anyList());
        verify(training, never()).getEquipment();
    }

    @Test
    void athleteCannotPrintTempo() {
        Training training = training(1, TrainingType.TEMPO);
        when(access.currentUser()).thenReturn(new AuthenticatedUser(1, "athlete", "", UserRole.ATHLETE, true));
        when(repository.findAllByIdIn(List.of(1L))).thenReturn(List.of(training));
        assertThrows(AccessDeniedException.class, () -> service.getPrintData(List.of(1L)));
        verifyNoInteractions(definitions, segments, sections);
    }

    @Test
    void coachCannotReadAnySelectedDataIfOneTrainingIsInaccessible() {
        Training first = training(1, TrainingType.FREE_TEXT);
        Training second = training(2, TrainingType.FREE_TEXT);
        when(access.currentUser()).thenReturn(new AuthenticatedUser(3, "coach", "", UserRole.COACH, true));
        when(repository.findAllByIdIn(List.of(1L, 2L))).thenReturn(List.of(first, second));
        when(assignments.canCoachManage(1, 3)).thenReturn(true);
        assertThrows(TrainingNotFoundException.class, () -> service.getPrintData(List.of(1L, 2L)));
        verifyNoInteractions(definitions, segments, sections);
    }

    private Training training(long id, TrainingType type) {
        Training result = mock(Training.class);
        when(result.getId()).thenReturn(id);
        when(result.getTrainingType()).thenReturn(type);
        when(result.getAssignmentType()).thenReturn(TrainingAssignmentType.GROUP);
        return result;
    }
}

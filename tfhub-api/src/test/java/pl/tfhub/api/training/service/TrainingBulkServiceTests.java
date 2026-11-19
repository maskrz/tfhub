package pl.tfhub.api.training.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.tfhub.api.common.authorization.AccessScopeService;
import pl.tfhub.api.training.entity.Training;
import pl.tfhub.api.training.enums.TrainingType;
import pl.tfhub.api.training.exception.InvalidTrainingRequestException;
import pl.tfhub.api.training.exception.TrainingNotFoundException;
import pl.tfhub.api.training.repository.TrainingAssignmentRepository;
import pl.tfhub.api.training.repository.TrainingRepository;
import pl.tfhub.api.user.enums.UserRole;
import pl.tfhub.api.user.service.AuthenticatedUser;

class TrainingBulkServiceTests {
    private final TrainingRepository repository = mock(TrainingRepository.class);
    private final TrainingAssignmentRepository assignments = mock(TrainingAssignmentRepository.class);
    private final AccessScopeService access = mock(AccessScopeService.class);
    private final TrainingDeletionService deletion = mock(TrainingDeletionService.class);
    private final TrainingCompletionService completion = mock(TrainingCompletionService.class);
    private final FreeTextTrainingService freeText = mock(FreeTextTrainingService.class);
    private final TrainingBulkService service = new TrainingBulkService(repository, assignments, access,
            deletion, completion, freeText);

    @BeforeEach
    void setUp() {
        when(access.currentUser()).thenReturn(new AuthenticatedUser(1, "admin", "", UserRole.ADMIN, true));
    }

    @Test
    void completesDatedPlansAndSkipsCompletedAndUndated() {
        Training tempo = training(1, TrainingType.TEMPO, LocalDate.now(), false);
        Training undated = training(2, TrainingType.FREE_TEXT, null, false);
        Training already = training(3, TrainingType.FREE_TEXT, LocalDate.now(), true);
        Training free = training(4, TrainingType.FREE_TEXT, LocalDate.now(), false);
        for (Training t : List.of(tempo, undated, already, free)) {
            when(repository.findByIdForUpdate(t.getId())).thenReturn(Optional.of(t));
        }
        var result = service.complete(List.of(4L, 3L, 2L, 1L));
        assertEquals(List.of(1L, 4L), result.completedIds());
        assertEquals(List.of(2L), result.skippedUndatedIds());
        assertEquals(List.of(3L), result.alreadyCompletedIds());
        verify(completion).completeTraining(eq(1L), argThat(request ->
                request.add().isEmpty() && request.update().isEmpty() && request.removeParticipationIds().isEmpty()));
        verify(freeText).markCompleted(4L);
    }

    @Test
    void authorizesEveryItemBeforeDeletingAnything() {
        when(access.currentUser()).thenReturn(new AuthenticatedUser(2, "coach", "", UserRole.COACH, true));
        Training first = training(1, TrainingType.TEMPO, null, false);
        Training second = training(2, TrainingType.FREE_TEXT, null, false);
        when(repository.findByIdForUpdate(1L)).thenReturn(Optional.of(first));
        when(repository.findByIdForUpdate(2L)).thenReturn(Optional.of(second));
        when(assignments.canCoachManage(1, 2)).thenReturn(true);
        assertThrows(TrainingNotFoundException.class, () -> service.delete(List.of(2L, 1L)));
        verifyNoInteractions(deletion);
    }

    @Test
    void rejectsDuplicateAndInvalidIds() {
        assertThrows(InvalidTrainingRequestException.class, () -> service.complete(List.of(1L, 1L)));
        assertThrows(InvalidTrainingRequestException.class, () -> service.delete(List.of(0L)));
        verifyNoInteractions(repository);
    }

    private Training training(long id, TrainingType type, LocalDate date, boolean completed) {
        Training result = mock(Training.class);
        when(result.getId()).thenReturn(id);
        when(result.getTrainingType()).thenReturn(type);
        when(result.getTrainingDate()).thenReturn(date);
        when(result.getStatus()).thenReturn(completed
                ? pl.tfhub.api.training.enums.TrainingStatus.COMPLETED
                : pl.tfhub.api.training.enums.TrainingStatus.PLANNED);
        return result;
    }
}

import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { EMPTY, catchError, finalize, take } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import {
  TrainingDetails,
  TrainingDefinitionDetails,
  TrainingExecutionDetails,
  TrainingParticipationDetails,
} from './training.model';
import { TrainingsApiService } from './trainings-api.service';
import { TrainingDeleteConfirmationComponent } from './training-delete-confirmation.component';
import { formatRestMinutes } from './training-rest-time';
import { formatFreeTextTraining, formatTempoTraining, formatTrainingDate } from './training-copy';

@Component({
  imports: [RouterLink, TrainingDeleteConfirmationComponent],
  selector: 'app-training-details-page',
  styleUrl: './training-details.page.scss',
  templateUrl: './training-details.page.html',
})
export class TrainingDetailsPage implements OnInit {
  readonly formatRestMinutes = formatRestMinutes;
  readonly training = signal<TrainingDetails | null>(null);
  readonly loading = signal(true);
  readonly errorMessage = signal<string | null>(null);
  readonly deletionDialogOpen = signal(false);
  readonly completing = signal(false);
  readonly actionError = signal<string | null>(null);
  readonly copying = signal(false);
  readonly copyFeedback = signal<{ target: number | 'training'; message: string; error: boolean } | null>(null);

  readonly auth = inject(AuthService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly trainingsApi = inject(TrainingsApiService);
  private readonly destroyRef = inject(DestroyRef);
  private trainingId = 0;
  private copyFeedbackTimer: ReturnType<typeof setTimeout> | undefined;

  constructor() {
    this.destroyRef.onDestroy(() => clearTimeout(this.copyFeedbackTimer));
  }

  get canManageTraining(): boolean {
    const role = this.auth.currentUser()?.role;
    return role === 'ADMIN' || role === 'COACH';
  }

  ngOnInit(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    if (!Number.isSafeInteger(id) || id <= 0) {
      this.loading.set(false);
      this.errorMessage.set('Nieprawidłowy identyfikator treningu.');
      return;
    }
    this.trainingId = id;
    this.loadTraining();
  }

  retry(): void {
    this.loadTraining();
  }

  openDeletionConfirmation(): void {
    if (this.training()) this.deletionDialogOpen.set(true);
  }

  closeDeletionConfirmation(): void {
    this.deletionDialogOpen.set(false);
  }

  completeFreeTextTraining(): void {
    const training = this.training();
    if (!training || !training.trainingDate || training.trainingType !== 'FREE_TEXT' || training.status !== 'PLANNED' || this.completing()) return;
    this.completing.set(true);
    this.actionError.set(null);
    this.trainingsApi.completeFreeText(training.id).pipe(
      take(1),
      catchError((error: unknown) => {
        this.actionError.set(this.getErrorMessage(error));
        return EMPTY;
      }),
      finalize(() => this.completing.set(false)),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe(() => this.loadTraining());
  }

  onTrainingDeleted(trainingId: number): void {
    if (trainingId !== this.training()?.id) return;
    this.deletionDialogOpen.set(false);
    void this.router.navigate(['/trainings']);
  }

  formatDate(date: string | null): string {
    return date ? formatTrainingDate(date) : 'Bez daty';
  }

  copyFreeTextTraining(): void {
    const training = this.training();
    if (!this.canManageTraining || !training || training.trainingType !== 'FREE_TEXT') return;
    void this.copyText(formatFreeTextTraining(training), 'training');
  }

  copyTempoDefinition(definition: TrainingDefinitionDetails): void {
    const training = this.training();
    if (!this.canManageTraining || !training || training.trainingType !== 'TEMPO') return;
    void this.copyText(formatTempoTraining(training, definition), definition.id);
  }

  private async copyText(text: string, target: number | 'training'): Promise<void> {
    if (this.copying()) return;
    this.copying.set(true);
    clearTimeout(this.copyFeedbackTimer);
    this.copyFeedback.set(null);
    try {
      if (!navigator.clipboard?.writeText) {
        throw new Error('Clipboard unavailable');
      }
      await navigator.clipboard.writeText(text);
      if (this.destroyRef.destroyed) return;
      this.copyFeedback.set({ target, message: 'Skopiowano do schowka', error: false });
      this.copyFeedbackTimer = setTimeout(() => this.copyFeedback.set(null), 3000);
    } catch {
      if (!this.destroyRef.destroyed) {
        this.copyFeedback.set({ target, message: 'Nie udało się skopiować do schowka. Spróbuj ponownie.', error: true });
      }
    } finally {
      this.copying.set(false);
    }
  }

  assignedAthleteNames(): string {
    return this.training()?.assignedAthletes
      .map((athlete) => `${athlete.firstName} ${athlete.lastName}`)
      .join(', ') ?? '';
  }

  resultLabel(execution: TrainingExecutionDetails | undefined): string {
    if (!execution) return 'Brak danych';
    switch (execution.status) {
      case 'RECORDED':
        return execution.measuredResult === null ? '' : this.formatResult(execution.measuredResult);
      case 'COMPLETED_WITHOUT_RESULT':
        return 'X';
      case 'NOT_COMPLETED':
        return '-';
      case 'PENDING':
        return '';
    }
  }

  executionFor(
    participant: TrainingParticipationDetails,
    segmentId: number,
  ): TrainingExecutionDetails | undefined {
    return participant.executions.find((execution) => execution.segmentId === segmentId);
  }

  private loadTraining(): void {
    this.loading.set(true);
    this.errorMessage.set(null);
    this.trainingsApi.getById(this.trainingId).pipe(
      take(1),
      catchError((error: unknown) => {
        this.errorMessage.set(this.getErrorMessage(error));
        return EMPTY;
      }),
      finalize(() => this.loading.set(false)),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe((training) => this.training.set(training));
  }

  private formatResult(seconds: number): string {
    const hundredths = Math.round(seconds * 100);
    const minutes = Math.floor(hundredths / 6000);
    const secondsWithinMinute = Math.floor((hundredths % 6000) / 100);
    const fraction = hundredths % 100;
    const secondsText = `${String(secondsWithinMinute).padStart(2, '0')},${String(fraction).padStart(2, '0')}`;
    return minutes > 0 ? `${minutes}:${secondsText}` : secondsText;
  }

  private getErrorMessage(error: unknown): string {
    if (error instanceof HttpErrorResponse && error.status === 401) {
      return 'Sesja wygasła lub użytkownik nie jest zalogowany.';
    }
    if (error instanceof HttpErrorResponse && error.status === 403) {
      return 'Brak dostępu do tego treningu.';
    }
    if (error instanceof HttpErrorResponse && error.status === 404) {
      return 'Nie znaleziono treningu.';
    }
    return 'Nie udało się pobrać danych treningu. Spróbuj ponownie.';
  }
}

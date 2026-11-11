import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { EMPTY, catchError, finalize, take } from 'rxjs';
import {
  TrainingDetails,
  TrainingExecutionDetails,
  TrainingParticipationDetails,
} from './training.model';
import { TrainingsApiService } from './trainings-api.service';

@Component({
  imports: [RouterLink],
  selector: 'app-training-details-page',
  styleUrl: './training-details.page.scss',
  templateUrl: './training-details.page.html',
})
export class TrainingDetailsPage implements OnInit {
  readonly training = signal<TrainingDetails | null>(null);
  readonly loading = signal(true);
  readonly errorMessage = signal<string | null>(null);

  private readonly route = inject(ActivatedRoute);
  private readonly trainingsApi = inject(TrainingsApiService);
  private readonly destroyRef = inject(DestroyRef);
  private trainingId = 0;

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

  formatDate(date: string): string {
    const [year, month, day] = date.split('-').map(Number);
    return new Intl.DateTimeFormat('pl-PL').format(new Date(year, month - 1, day));
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

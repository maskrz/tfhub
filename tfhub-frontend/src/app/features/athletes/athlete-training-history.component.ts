import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, Input, OnChanges, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { EMPTY, catchError, finalize, take } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { formatResult } from '../trainings/training-time';
import { AthleteTrainingHistory, AthleteTrainingHistoryFilters, AthleteTrainingHistorySegment } from './athlete-profile.model';
import { AthletesApiService } from './athletes-api.service';

@Component({
  selector: 'app-athlete-training-history',
  imports: [RouterLink],
  templateUrl: './athlete-training-history.component.html',
  styleUrl: './athlete-training-history.component.scss',
})
export class AthleteTrainingHistoryComponent implements OnChanges {
  @Input({ required: true }) athleteId = 0;

  readonly fromDate = signal('');
  readonly toDate = signal('');
  readonly filterError = signal<string | null>(null);
  readonly error = signal<string | null>(null);
  readonly loading = signal(false);
  readonly loaded = signal(false);
  readonly history = signal<AthleteTrainingHistory[]>([]);
  readonly selectedDistances = signal<number[]>([]);
  readonly distances = computed(() => [...new Set(
    this.history().flatMap((training) => training.segments.map((segment) => segment.distanceM)),
  )].sort((a, b) => a - b));
  readonly allSelected = computed(() =>
    this.distances().length > 0 && this.distances().every((distance) => this.selectedDistances().includes(distance)));
  readonly auth = inject(AuthService);
  private readonly api = inject(AthletesApiService);
  private readonly destroyRef = inject(DestroyRef);
  private requestId = 0;
  private retryFilters: AthleteTrainingHistoryFilters | null = null;

  ngOnChanges(): void {
    this.requestId++;
    this.fromDate.set('');
    this.toDate.set('');
    this.filterError.set(null);
    this.error.set(null);
    this.loading.set(false);
    this.loaded.set(false);
    this.history.set([]);
    this.selectedDistances.set([]);
    this.retryFilters = null;
  }

  setDate(event: Event, bound: 'from' | 'to'): void {
    const input = event.target;
    if (!(input instanceof HTMLInputElement)) return;
    (bound === 'from' ? this.fromDate : this.toDate).set(input.value);
    this.filterError.set(null);
  }

  setQuickRange(months: number): void {
    const today = new Date();
    const lastDay = new Date(today.getFullYear(), today.getMonth() - months + 1, 0).getDate();
    const start = new Date(today.getFullYear(), today.getMonth() - months, Math.min(today.getDate(), lastDay));
    this.fromDate.set(this.dateValue(start));
    this.toDate.set(this.dateValue(today));
    this.filterError.set(null);
  }

  clearDates(): void {
    this.fromDate.set('');
    this.toDate.set('');
    this.filterError.set(null);
  }

  load(): void {
    if (this.loading()) return;
    const fromDate = this.fromDate();
    const toDate = this.toDate();
    if (fromDate && toDate && fromDate > toDate) {
      this.filterError.set('Data początkowa nie może być późniejsza niż data końcowa.');
      return;
    }
    this.filterError.set(null);
    this.fetch({ ...(fromDate ? { fromDate } : {}), ...(toDate ? { toDate } : {}) });
  }

  retry(): void {
    if (!this.loading() && this.retryFilters) this.fetch(this.retryFilters);
  }

  toggleDistance(distance: number, event: Event): void {
    const checkbox = event.target;
    if (!(checkbox instanceof HTMLInputElement)) return;
    this.selectedDistances.update((selected) => checkbox.checked
      ? [...selected.filter((value) => value !== distance), distance]
      : selected.filter((value) => value !== distance));
  }

  toggleAll(): void {
    this.selectedDistances.set(this.allSelected() ? [] : [...this.distances()]);
  }

  visibleSegments(training: AthleteTrainingHistory): AthleteTrainingHistorySegment[] {
    const selected = this.selectedDistances();
    return training.segments.filter((segment) => selected.includes(segment.distanceM));
  }

  statusLabel(segment: AthleteTrainingHistorySegment): string {
    switch (segment.status) {
      case 'PENDING': return 'Oczekuje na uzupełnienie';
      case 'COMPLETED_WITHOUT_RESULT': return 'Ukończono bez wyniku';
      case 'NOT_COMPLETED': return 'Nie ukończono';
      case 'RECORDED': return 'Zapisany czas';
    }
  }

  resultLabel(segment: AthleteTrainingHistorySegment): string {
    switch (segment.status) {
      case 'PENDING': return '';
      case 'COMPLETED_WITHOUT_RESULT': return 'X';
      case 'NOT_COMPLETED': return '–';
      case 'RECORDED':
        if (segment.measuredResult === null) return '';
        return formatResult(segment.measuredResult, Math.round(segment.measuredResult * 1000) % 10 === 0 ? 2 : 3);
    }
  }

  formatDate(date: string): string {
    const [year, month, day] = date.split('-').map(Number);
    return new Intl.DateTimeFormat('pl-PL', { day: '2-digit', month: '2-digit', year: 'numeric' })
      .format(new Date(year, month - 1, day));
  }

  private dateValue(date: Date): string {
    return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
  }

  private fetch(filters: AthleteTrainingHistoryFilters): void {
    const requestId = ++this.requestId;
    this.retryFilters = { ...filters };
    this.loading.set(true);
    this.error.set(null);
    this.api.getTrainingHistory(this.athleteId, filters).pipe(
      take(1),
      catchError((error: unknown) => {
        if (requestId === this.requestId) {
          this.error.set(error instanceof HttpErrorResponse && error.status === 401
            ? 'Sesja wygasła lub użytkownik nie jest zalogowany.'
            : 'Nie udało się pobrać historii treningów. Spróbuj ponownie.');
        }
        return EMPTY;
      }),
      finalize(() => {
        if (requestId === this.requestId) this.loading.set(false);
      }),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe((history) => {
      if (requestId !== this.requestId) return;
      this.history.set([...history].sort((a, b) =>
        b.trainingDate.localeCompare(a.trainingDate) || b.trainingId - a.trainingId));
      this.selectedDistances.set([...this.distances()]);
      this.loaded.set(true);
    });
  }
}

import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { EMPTY, catchError, finalize } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { MultiSelectDropdownComponent, MultiSelectOption } from '../../shared/multi-select-dropdown.component';
import { TrainingListItem, TrainingStatus } from './training.model';
import { TrainingsApiService } from './trainings-api.service';
import { TrainingDeleteConfirmationComponent } from './training-delete-confirmation.component';
import { TrainingImportUploadModalComponent } from '../training-imports/training-import-upload-modal.component';
import { TrainingImportListItem, TrainingImportStatus, TrainingImportUploadResponse } from '../training-imports/training-import.model';
import { TrainingImportsApiService } from '../training-imports/training-imports-api.service';

@Component({
  imports: [
    MultiSelectDropdownComponent,
    RouterLink,
    TrainingDeleteConfirmationComponent,
    TrainingImportUploadModalComponent,
  ],
  selector: 'app-trainings-page',
  styleUrl: './trainings.page.scss',
  templateUrl: './trainings.page.html',
})
export class TrainingsPage implements OnInit {
  readonly allTrainings = signal<TrainingListItem[]>([]);
  readonly loading = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly successMessage = signal<string | null>(null);
  readonly trainingPendingDeletion = signal<TrainingListItem | null>(null);
  readonly importModalOpen = signal(false);
  readonly imports = signal<TrainingImportListItem[]>([]);
  readonly importsLoading = signal(false);
  readonly importsError = signal<string | null>(null);
  readonly retryingImportIds = signal<number[]>([]);
  readonly importRetryErrors = signal<Record<number, string>>({});
  readonly query = signal('');
  readonly selectedGroupIds = signal<number[]>([]);
  readonly statusFilter = signal<'' | TrainingStatus>('');
  readonly trainingDateFrom = signal('');
  readonly trainingDateTo = signal('');
  readonly sortColumn = signal<'name' | 'date' | 'group' | 'participants'>('date');
  readonly sortDirection = signal<'asc' | 'desc'>('desc');
  readonly groups = computed(() => {
    const groups = new Map<number, string>();
    for (const training of this.allTrainings()) {
      groups.set(training.trainingGroup.id, training.trainingGroup.name);
    }
    return [...groups.entries()]
      .map(([id, name]) => ({ id, name }))
      .sort((left, right) => left.name.localeCompare(right.name, 'pl', { sensitivity: 'base' }));
  });
  readonly groupOptions = computed<MultiSelectOption[]>(() =>
    this.groups().map((group) => ({ id: group.id, name: group.name })),
  );
  readonly visibleTrainings = computed(() => {
    const terms = this.query().trim().toLocaleLowerCase('pl').split(/\s+/).filter(Boolean);
    const groupIds = this.selectedGroupIds();
    const status = this.statusFilter();
    const dateFrom = this.trainingDateFrom();
    const dateTo = this.trainingDateTo();
    const direction = this.sortDirection() === 'asc' ? 1 : -1;
    return this.allTrainings()
      .filter((training) => {
        if (status && training.status !== status) return false;
        if (groupIds.length > 0 && !groupIds.includes(training.trainingGroup.id)) return false;
        if (dateFrom && training.trainingDate < dateFrom) return false;
        if (dateTo && training.trainingDate > dateTo) return false;
        const searchable = `${training.name} ${training.trainingGroup.name}`.toLocaleLowerCase('pl');
        return terms.every((term) => searchable.includes(term));
      })
      .sort((left, right) => {
        if (this.sortColumn() === 'date') {
          const dateComparison = left.trainingDate.localeCompare(right.trainingDate);
          return dateComparison === 0
            ? (left.id - right.id) * direction
            : dateComparison * direction;
        }
        if (this.sortColumn() === 'participants') {
          return (left.participantCount - right.participantCount) * direction;
        }
        const leftValue = this.sortColumn() === 'name' ? left.name : left.trainingGroup.name;
        const rightValue = this.sortColumn() === 'name' ? right.name : right.trainingGroup.name;
        return leftValue.localeCompare(rightValue, 'pl', { sensitivity: 'base', numeric: true }) * direction;
      });
  });

  readonly auth = inject(AuthService);
  private readonly api = inject(TrainingsApiService);
  private readonly importsApi = inject(TrainingImportsApiService);
  private readonly destroyRef = inject(DestroyRef);
  private importRefreshTimer: ReturnType<typeof setTimeout> | null = null;
  private importRefreshPending = false;

  ngOnInit(): void {
    this.loadTrainings();
    this.refreshImports();
    this.destroyRef.onDestroy(() => {
      if (this.importRefreshTimer !== null) clearTimeout(this.importRefreshTimer);
    });
  }

  onSearchInput(event: Event): void {
    const input = event.target;
    if (input instanceof HTMLInputElement) {
      this.query.set(input.value);
    }
  }

  onGroupIdsChange(groupIds: number[]): void {
    this.selectedGroupIds.set(groupIds);
  }

  onStatusChange(event: Event): void {
    const select = event.target;
    if (!(select instanceof HTMLSelectElement)) return;
    const value = select.value;
    if (value === '' || value === 'PLANNED' || value === 'COMPLETED') {
      this.statusFilter.set(value);
    }
  }

  onTrainingDateChange(event: Event, bound: 'from' | 'to'): void {
    const input = event.target;
    if (input instanceof HTMLInputElement) {
      if (bound === 'from') {
        this.trainingDateFrom.set(input.value);
      } else {
        this.trainingDateTo.set(input.value);
      }
    }
  }

  sortBy(column: 'name' | 'date' | 'group' | 'participants'): void {
    if (this.sortColumn() === column) {
      this.sortDirection.update((direction) => direction === 'asc' ? 'desc' : 'asc');
      return;
    }
    this.sortColumn.set(column);
    this.sortDirection.set(column === 'date' ? 'desc' : 'asc');
  }

  get hasSearchQuery(): boolean {
    return this.query().trim().length > 0 ||
      this.selectedGroupIds().length > 0 ||
      this.trainingDateFrom().length > 0 ||
      this.trainingDateTo().length > 0 ||
      this.statusFilter() !== '';
  }

  retry(): void {
    this.loadTrainings();
  }

  openDeletionConfirmation(training: TrainingListItem): void {
    this.successMessage.set(null);
    this.trainingPendingDeletion.set(training);
  }

  closeDeletionConfirmation(): void {
    this.trainingPendingDeletion.set(null);
  }

  openImportModal(): void {
    this.successMessage.set(null);
    this.importModalOpen.set(true);
  }

  closeImportModal(): void {
    this.importModalOpen.set(false);
  }

  onTrainingImportUploaded(response: TrainingImportUploadResponse): void {
    this.importModalOpen.set(false);
    this.successMessage.set(response.possibleDuplicate
      ? 'Zdjęcie przesłano. Możliwy duplikat: istnieje podobny import treningu.'
      : 'Zdjęcie przesłano do przetworzenia.');
    this.refreshImports();
  }

  refreshImports(): void {
    if (this.importRefreshTimer !== null) {
      clearTimeout(this.importRefreshTimer);
      this.importRefreshTimer = null;
    }
    if (this.importsLoading()) {
      this.importRefreshPending = true;
      return;
    }
    this.importsLoading.set(true);
    this.importsApi.getActive().pipe(
      catchError((error: unknown) => {
        this.importsError.set(this.getImportErrorMessage(error, 'Nie udało się pobrać importów treningów.'));
        return EMPTY;
      }),
      finalize(() => {
        this.importsLoading.set(false);
        if (this.destroyRef.destroyed) return;
        if (this.importRefreshPending) {
          this.importRefreshPending = false;
          this.refreshImports();
        } else if (this.imports().some((item) => this.importMayChange(item))) {
          this.importRefreshTimer = setTimeout(() => this.refreshImports(), 5000);
        }
      }),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe((imports) => {
      this.imports.set(imports);
      this.importsError.set(null);
    });
  }

  retryImport(item: TrainingImportListItem): void {
    if (item.status !== 'FAILED' || this.retryingImportIds().includes(item.id)) return;
    this.retryingImportIds.update((ids) => [...ids, item.id]);
    this.importRetryErrors.update((errors) => {
      const updated = { ...errors };
      delete updated[item.id];
      return updated;
    });
    this.importsApi.retry(item.id).pipe(
      catchError((error: unknown) => {
        this.importRetryErrors.update((errors) => ({
          ...errors,
          [item.id]: this.getImportErrorMessage(error, 'Nie udało się ponowić przetwarzania.'),
        }));
        return EMPTY;
      }),
      finalize(() => this.retryingImportIds.update((ids) => ids.filter((id) => id !== item.id))),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe((response) => {
      this.imports.update((items) => items.map((current) => current.id === item.id
        ? {
          ...current,
          status: response.status,
          nextRetryAt: response.nextRetryAt,
          failureMessage: response.latestAttempt?.failureMessage ?? null,
        }
        : current));
      this.refreshImports();
    });
  }

  importStatusLabel(status: TrainingImportStatus): string {
    const labels: Record<TrainingImportStatus, string> = {
      PENDING: 'Oczekuje na przetworzenie',
      PROCESSING: 'Przetwarzanie',
      REVIEW_REQUIRED: 'Wymaga weryfikacji',
      FAILED: 'Błąd przetwarzania',
      ACCEPTED: 'Zaakceptowany',
      REJECTED: 'Odrzucony',
    };
    return labels[status];
  }

  private importMayChange(item: TrainingImportListItem): boolean {
    return item.status === 'PENDING' || item.status === 'PROCESSING'
      || (item.status === 'FAILED' && item.nextRetryAt !== null);
  }

  private getImportErrorMessage(error: unknown, fallback: string): string {
    if (error instanceof HttpErrorResponse && error.status === 401) {
      return 'Sesja wygasła lub użytkownik nie jest zalogowany.';
    }
    if (error instanceof HttpErrorResponse && error.status === 403) {
      return 'Brak dostępu do importów treningów.';
    }
    return fallback;
  }

  onTrainingDeleted(trainingId: number): void {
    this.allTrainings.update((current) => current.filter((training) => training.id !== trainingId));
    this.trainingPendingDeletion.set(null);
    this.successMessage.set('Trening został trwale usunięty.');
  }

  formatDate(date: string): string {
    const [year, month, day] = date.split('-').map(Number);
    return new Intl.DateTimeFormat('pl-PL').format(new Date(year, month - 1, day));
  }

  private loadTrainings(): void {
    this.loading.set(true);
    this.errorMessage.set(null);
    this.api
      .getAll(true)
      .pipe(
        catchError((error: unknown) => {
          this.allTrainings.set([]);
          this.errorMessage.set(this.getErrorMessage(error));
          return EMPTY;
        }),
        finalize(() => this.loading.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((trainings) => this.allTrainings.set(trainings));
  }

  private getErrorMessage(error: unknown): string {
    if (error instanceof HttpErrorResponse && error.status === 401) {
      return 'Sesja wygasła lub użytkownik nie jest zalogowany.';
    }
    if (error instanceof HttpErrorResponse && error.status === 403) {
      return 'Brak dostępu do listy treningów.';
    }
    return 'Nie udało się pobrać listy treningów. Spróbuj ponownie.';
  }

}

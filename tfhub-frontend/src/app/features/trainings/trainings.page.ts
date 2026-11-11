import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { EMPTY, catchError, finalize } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { MultiSelectDropdownComponent, MultiSelectOption } from '../../shared/multi-select-dropdown.component';
import { TrainingListItem } from './training.model';
import { TrainingsApiService } from './trainings-api.service';

@Component({
  imports: [MultiSelectDropdownComponent, RouterLink],
  selector: 'app-trainings-page',
  styleUrl: './trainings.page.scss',
  templateUrl: './trainings.page.html',
})
export class TrainingsPage implements OnInit {
  readonly allTrainings = signal<TrainingListItem[]>([]);
  readonly loading = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly query = signal('');
  readonly selectedGroupIds = signal<number[]>([]);
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
    const dateFrom = this.trainingDateFrom();
    const dateTo = this.trainingDateTo();
    const direction = this.sortDirection() === 'asc' ? 1 : -1;
    return this.allTrainings()
      .filter((training) => {
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
  private readonly destroyRef = inject(DestroyRef);

  ngOnInit(): void {
    this.loadTrainings();
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
      this.trainingDateTo().length > 0;
  }

  retry(): void {
    this.loadTrainings();
  }

  formatDate(date: string): string {
    const [year, month, day] = date.split('-').map(Number);
    return new Intl.DateTimeFormat('pl-PL').format(new Date(year, month - 1, day));
  }

  private loadTrainings(): void {
    this.loading.set(true);
    this.errorMessage.set(null);
    this.api
      .getAll()
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

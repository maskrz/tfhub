import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { EMPTY, catchError, finalize, forkJoin } from 'rxjs';
import { AthleteResponse } from '../athletes/athlete.model';
import { AthletesApiService } from '../athletes/athletes-api.service';
import { TestResponse } from '../tests/test.model';
import { TestsApiService } from '../tests/tests-api.service';
import { CreateMultipleResultsResponse, ResultResponse, ResultRow, ResultType } from './result.model';
import { ResultsApiService } from './results-api.service';
import { ResultFormModalComponent } from './result-form-modal.component';
import { MultipleResultsFormModalComponent } from './multiple-results-form-modal.component';
import { DeleteResultConfirmationComponent } from './delete-result-confirmation.component';

@Component({
  imports: [ResultFormModalComponent, MultipleResultsFormModalComponent, DeleteResultConfirmationComponent],
  selector: 'app-results-page',
  styleUrl: './results.page.scss',
  templateUrl: './results.page.html',
})
export class ResultsPage implements OnInit {
  readonly allResults = signal<ResultRow[]>([]);
  readonly athletes = signal<AthleteResponse[]>([]);
  readonly tests = signal<TestResponse[]>([]);
  readonly loading = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly query = signal('');
  readonly selectedAthleteId = signal('');
  readonly selectedTestId = signal('');
  readonly fromDate = signal('');
  readonly toDate = signal('');
  readonly selectedType = signal<ResultType | ''>('');
  readonly sortDirection = signal<'asc' | 'desc'>('desc');
  readonly isCreateFormOpen = signal(false);
  readonly selectedResult = signal<ResultRow | null>(null);
  readonly resultPendingDelete = signal<ResultRow | null>(null);
  readonly isMultipleCreateFormOpen = signal(false);
  readonly successMessage = signal<string | null>(null);
  readonly visibleResults = computed(() => {
    const terms = this.query().trim().toLocaleLowerCase('pl-PL').split(/\s+/).filter(Boolean);
    const athleteId = this.selectedAthleteId();
    const testId = this.selectedTestId();
    const fromDate = this.fromDate();
    const toDate = this.toDate();
    const type = this.selectedType();
    const direction = this.sortDirection() === 'asc' ? 1 : -1;

    return this.allResults()
      .filter((result) => {
        if (athleteId && result.athleteId !== Number(athleteId)) return false;
        if (testId && result.testId !== Number(testId)) return false;
        if (fromDate && result.resultDate < fromDate) return false;
        if (toDate && result.resultDate > toDate) return false;
        if (type && result.type !== type) return false;

        const searchableText = `${result.athleteName} ${result.testName}`.toLocaleLowerCase('pl-PL');
        return terms.every((term) => searchableText.includes(term));
      })
      .sort((left, right) => {
        const dateComparison = left.resultDate.localeCompare(right.resultDate);
        if (dateComparison !== 0) return dateComparison * direction;
        return (left.id - right.id) * direction;
      });
  });

  private readonly api = inject(ResultsApiService);
  private readonly athletesApi = inject(AthletesApiService);
  private readonly testsApi = inject(TestsApiService);
  private readonly destroyRef = inject(DestroyRef);

  ngOnInit(): void {
    this.loadResults();
  }

  onSearchInput(event: Event): void {
    const input = event.target;
    if (input instanceof HTMLInputElement) {
      this.query.set(input.value);
    }
  }

  onAthleteChange(event: Event): void {
    const select = event.target;
    if (select instanceof HTMLSelectElement) {
      this.selectedAthleteId.set(select.value);
    }
  }

  onTestChange(event: Event): void {
    const select = event.target;
    if (select instanceof HTMLSelectElement) {
      this.selectedTestId.set(select.value);
    }
  }

  onDateChange(event: Event, bound: 'from' | 'to'): void {
    const input = event.target;
    if (input instanceof HTMLInputElement) {
      if (bound === 'from') {
        this.fromDate.set(input.value);
      } else {
        this.toDate.set(input.value);
      }
    }
  }

  onTypeChange(event: Event): void {
    const select = event.target;
    if (select instanceof HTMLSelectElement) {
      const value = select.value;
      if (value === '' || value === 'TRAINING' || value === 'COMPETITION') {
        this.selectedType.set(value);
      }
    }
  }

  toggleDateSort(): void {
    this.sortDirection.update((direction) => direction === 'asc' ? 'desc' : 'asc');
  }

  retry(): void {
    this.loadResults();
  }

  resultTypeLabel(type: ResultType): string {
    return type === 'TRAINING' ? 'Trening' : 'Zawody';
  }

  formatDate(date: string): string {
    const [year, month, day] = date.split('-').map(Number);
    return new Intl.DateTimeFormat('pl-PL', {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
      timeZone: 'UTC',
    }).format(new Date(Date.UTC(year, month - 1, day)));
  }

  formatValue(value: number): string {
    return new Intl.NumberFormat('pl-PL', { maximumFractionDigits: 3 }).format(value);
  }

  hasActiveFilters(): boolean {
    return Boolean(
      this.query().trim() ||
      this.selectedAthleteId() ||
      this.selectedTestId() ||
      this.fromDate() ||
      this.toDate() ||
      this.selectedType(),
    );
  }

  openCreateForm(): void {
    this.successMessage.set(null);
    this.isCreateFormOpen.set(true);
  }

  closeCreateForm(): void {
    this.isCreateFormOpen.set(false);
    this.selectedResult.set(null);
  }

  openEditForm(result: ResultRow): void {
    this.successMessage.set(null);
    this.selectedResult.set(result);
    this.isCreateFormOpen.set(true);
  }

  openDeleteConfirmation(result: ResultRow): void {
    this.successMessage.set(null);
    this.resultPendingDelete.set(result);
  }

  closeDeleteConfirmation(): void {
    this.resultPendingDelete.set(null);
  }

  onResultDeleted(resultId: number): void {
    this.allResults.update((current) => current.filter((result) => result.id !== resultId));
    this.resultPendingDelete.set(null);
    this.successMessage.set('Wynik został usunięty.');
  }

  onResultSaved(result: ResultResponse): void {
    const [row] = this.joinResults([result], this.athletes(), this.tests());
    const editing = this.selectedResult() !== null;
    this.allResults.update((current) =>
      editing
        ? current.map((existing) => existing.id === row.id ? row : existing)
        : [...current, row],
    );
    this.isCreateFormOpen.set(false);
    this.selectedResult.set(null);
    this.successMessage.set(editing ? 'Wynik został zaktualizowany.' : 'Wynik został dodany.');
  }

  openMultipleCreateForm(): void {
    this.successMessage.set(null);
    this.isMultipleCreateFormOpen.set(true);
  }

  closeMultipleCreateForm(): void {
    this.isMultipleCreateFormOpen.set(false);
  }

  onResultsCreated(response: CreateMultipleResultsResponse): void {
    const rows = this.joinResults(response.results, this.athletes(), this.tests());
    this.allResults.update((current) => [...current, ...rows]);
    this.isMultipleCreateFormOpen.set(false);
    this.successMessage.set(this.multipleResultsSuccessMessage(response.createdCount));
  }

  private multipleResultsSuccessMessage(count: number): string {
    const lastTwoDigits = count % 100;
    const lastDigit = count % 10;
    let noun = 'wyników';
    if (count === 1) {
      noun = 'wynik';
    } else if (lastTwoDigits < 12 || lastTwoDigits > 14) {
      if (lastDigit >= 2 && lastDigit <= 4) noun = 'wyniki';
    }
    return `Dodano ${count} ${noun}.`;
  }

  private loadResults(): void {
    this.loading.set(true);
    this.errorMessage.set(null);
    forkJoin({
      results: this.api.getAll(),
      athletes: this.athletesApi.getAll(),
      tests: this.testsApi.getAll(),
    })
      .pipe(
        catchError((error: unknown) => {
          this.allResults.set([]);
          this.athletes.set([]);
          this.tests.set([]);
          this.errorMessage.set(this.getErrorMessage(error));
          return EMPTY;
        }),
        finalize(() => this.loading.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe(({ results, athletes, tests }) => {
        this.athletes.set(athletes);
        this.tests.set(tests);
        this.allResults.set(this.joinResults(results, athletes, tests));
      });
  }

  private joinResults(
    results: ResultResponse[],
    athletes: AthleteResponse[],
    tests: TestResponse[],
  ): ResultRow[] {
    const athleteById = new Map(athletes.map((athlete) => [athlete.id, athlete]));
    const testById = new Map(tests.map((test) => [test.id, test]));

    return results.map((result) => {
      const athlete = athleteById.get(result.athleteId);
      const test = testById.get(result.testId);
      return {
        ...result,
        athleteName: athlete ? `${athlete.firstName} ${athlete.lastName}` : `Zawodnik #${result.athleteId}`,
        testName: test?.name ?? `Test #${result.testId}`,
        unit: test?.unit ?? '',
      };
    });
  }

  private getErrorMessage(error: unknown): string {
    if (error instanceof HttpErrorResponse && error.status === 401) {
      return 'Sesja wygasła lub użytkownik nie jest zalogowany.';
    }
    if (error instanceof HttpErrorResponse && error.status === 403) {
      return 'Brak dostępu do listy wyników.';
    }
    return 'Nie udało się pobrać wyników. Spróbuj ponownie.';
  }
}

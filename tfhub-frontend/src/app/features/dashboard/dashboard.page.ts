import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { EMPTY, catchError, finalize, forkJoin, take } from 'rxjs';
import { AthleteFormModalComponent } from '../athletes/athlete-form-modal.component';
import { AthleteResponse } from '../athletes/athlete.model';
import { AthletesApiService } from '../athletes/athletes-api.service';
import { MultipleResultsFormModalComponent } from '../results/multiple-results-form-modal.component';
import { CreateMultipleResultsResponse, ResultResponse } from '../results/result.model';
import { ResultFormModalComponent } from '../results/result-form-modal.component';
import { TestResponse } from '../tests/test.model';
import { TestsApiService } from '../tests/tests-api.service';
import { ClubRecords, DashboardOverview } from './statistics.model';
import { StatisticsApiService } from './statistics-api.service';

@Component({
  imports: [
    RouterLink,
    AthleteFormModalComponent,
    ResultFormModalComponent,
    MultipleResultsFormModalComponent,
  ],
  selector: 'app-dashboard-page',
  styleUrl: './dashboard.page.scss',
  templateUrl: './dashboard.page.html',
})
export class DashboardPage implements OnInit {
  readonly overview = signal<DashboardOverview | null>(null);
  readonly overviewLoading = signal(false);
  readonly overviewError = signal<string | null>(null);
  readonly selectedTestId = signal('');
  readonly selectedSeason = signal('');
  readonly records = signal<ClubRecords | null>(null);
  readonly recordsLoading = signal(false);
  readonly recordsError = signal<string | null>(null);
  readonly lookupLoading = signal(false);
  readonly lookupError = signal<string | null>(null);
  readonly isAthleteFormOpen = signal(false);
  readonly isResultFormOpen = signal(false);
  readonly isMultipleResultFormOpen = signal(false);
  readonly athletes = signal<AthleteResponse[]>([]);
  readonly tests = signal<TestResponse[]>([]);

  private readonly statisticsApi = inject(StatisticsApiService);
  private readonly athletesApi = inject(AthletesApiService);
  private readonly testsApi = inject(TestsApiService);
  private readonly destroyRef = inject(DestroyRef);
  private athleteCache: AthleteResponse[] | null = null;
  private testCache: TestResponse[] | null = null;
  private overviewRequestId = 0;
  private recordsRequestId = 0;

  ngOnInit(): void {
    this.refreshOverview();
  }

  refreshOverview(): void {
    const requestId = ++this.overviewRequestId;
    const currentTestId = this.selectedTestId();
    const currentSeason = this.selectedSeason();
    this.overviewLoading.set(true);
    this.overviewError.set(null);

    this.statisticsApi
      .getDashboardOverview()
      .pipe(
        take(1),
        catchError((error: unknown) => {
          if (requestId === this.overviewRequestId) {
            this.overviewError.set(this.getErrorMessage(error, 'overview'));
          }
          return EMPTY;
        }),
        finalize(() => {
          if (requestId === this.overviewRequestId) this.overviewLoading.set(false);
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((overview) => {
        if (requestId !== this.overviewRequestId) return;
        this.overview.set(overview);

        const validTest = overview.availableTests.some((test) => String(test.id) === currentTestId);
        const nextTestId = validTest ? currentTestId : String(overview.availableTests[0]?.id ?? '');
        this.selectedTestId.set(nextTestId);
        this.selectedSeason.set(
          currentSeason && overview.availableSeasons.includes(Number(currentSeason)) ? currentSeason : '',
        );
        this.loadRecords();
      });
  }

  onTestChange(event: Event): void {
    const select = event.target;
    if (select instanceof HTMLSelectElement && this.selectedTestId() !== select.value) {
      this.selectedTestId.set(select.value);
      this.loadRecords();
    }
  }

  onSeasonChange(event: Event): void {
    const select = event.target;
    if (select instanceof HTMLSelectElement && this.selectedSeason() !== select.value) {
      this.selectedSeason.set(select.value);
      this.loadRecords();
    }
  }

  retryOverview(): void {
    this.refreshOverview();
  }

  retryRecords(): void {
    this.loadRecords();
  }

  openAthleteForm(): void {
    this.lookupError.set(null);
    this.isAthleteFormOpen.set(true);
  }

  openResultForm(): void {
    this.loadFormLookups(() => this.isResultFormOpen.set(true));
  }

  openMultipleResultForm(): void {
    this.loadFormLookups(() => this.isMultipleResultFormOpen.set(true));
  }

  onAthleteCreated(athlete: AthleteResponse): void {
    this.athleteCache = [...(this.athleteCache ?? []), athlete];
    this.athletes.set(this.athleteCache);
    this.isAthleteFormOpen.set(false);
    this.refreshOverview();
  }

  onResultCreated(_result: ResultResponse): void {
    this.isResultFormOpen.set(false);
    this.refreshOverview();
  }

  onMultipleResultsCreated(_response: CreateMultipleResultsResponse): void {
    this.isMultipleResultFormOpen.set(false);
    this.refreshOverview();
  }

  closeAthleteForm(): void {
    this.isAthleteFormOpen.set(false);
  }

  closeResultForm(): void {
    this.isResultFormOpen.set(false);
  }

  closeMultipleResultForm(): void {
    this.isMultipleResultFormOpen.set(false);
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

  private loadRecords(): void {
    const requestId = ++this.recordsRequestId;
    const testId = Number(this.selectedTestId());
    const season = this.selectedSeason() ? Number(this.selectedSeason()) : null;
    this.recordsError.set(null);
    this.records.set(null);

    if (!testId) {
      this.recordsLoading.set(false);
      return;
    }

    this.recordsLoading.set(true);
    this.statisticsApi
      .getClubRecords(testId, season)
      .pipe(
        take(1),
        catchError((error: unknown) => {
          if (requestId === this.recordsRequestId) {
            this.recordsError.set(this.getErrorMessage(error, 'records'));
          }
          return EMPTY;
        }),
        finalize(() => {
          if (requestId === this.recordsRequestId) this.recordsLoading.set(false);
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((records) => {
        if (
          requestId === this.recordsRequestId &&
          testId === Number(this.selectedTestId()) &&
          season === (this.selectedSeason() ? Number(this.selectedSeason()) : null)
        ) {
          this.records.set(records);
        }
      });
  }

  private loadFormLookups(onLoaded: () => void): void {
    if (this.athleteCache && this.testCache) {
      this.athletes.set(this.athleteCache);
      this.tests.set(this.testCache);
      onLoaded();
      return;
    }

    if (this.lookupLoading()) return;
    this.lookupLoading.set(true);
    this.lookupError.set(null);
    forkJoin({
      athletes: this.athletesApi.getAll(),
      tests: this.testsApi.getAll(),
    })
      .pipe(
        take(1),
        catchError((error: unknown) => {
          this.lookupError.set(this.getErrorMessage(error, 'lookups'));
          return EMPTY;
        }),
        finalize(() => this.lookupLoading.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe(({ athletes, tests }) => {
        this.athleteCache = athletes;
        this.testCache = tests;
        this.athletes.set(athletes);
        this.tests.set(tests);
        onLoaded();
      });
  }

  private getErrorMessage(error: unknown, request: 'overview' | 'records' | 'lookups'): string {
    if (error instanceof HttpErrorResponse && error.status === 401) {
      return 'Sesja wygasła lub użytkownik nie jest zalogowany.';
    }
    if (error instanceof HttpErrorResponse && error.status === 403) {
      return 'Brak dostępu do danych panelu głównego.';
    }
    if (request === 'records' && error instanceof HttpErrorResponse && error.status === 404) {
      return 'Nie znaleziono wybranego testu.';
    }
    if (request === 'lookups') {
      return 'Nie udało się pobrać zawodników i testów do formularza. Spróbuj ponownie.';
    }
    return request === 'records'
      ? 'Nie udało się pobrać rekordów klubowych. Spróbuj ponownie.'
      : 'Nie udało się pobrać danych panelu głównego. Spróbuj ponownie.';
  }
}

import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { EMPTY, Subscription, catchError, finalize, forkJoin, interval, switchMap, take, takeWhile } from 'rxjs';
import { AthleteFormModalComponent } from './athlete-form-modal.component';
import { AthleteResponse, Gender } from './athlete.model';
import { AthleteProfileResult, AthleteResultHistoryFilters } from './athlete-profile.model';
import { AthletesApiService } from './athletes-api.service';
import { TestCategory, TestResponse } from '../tests/test.model';
import { TestsApiService } from '../tests/tests-api.service';
import { ResultFormModalComponent } from '../results/result-form-modal.component';
import { ResultResponse } from '../results/result.model';
import { AuthService } from '../../core/auth/auth.service';
import { AthleteTrainingHistoryComponent } from './athlete-training-history.component';
import { CompetitionApiService } from '../competition/competition-api.service';
import { CompetitionResultFormComponent } from '../competition/competition-result-form.component';
import { ModalDialogDirective } from '../../shared/modal-dialog.directive';
import {
  CompetitionResult,
  CompetitionSyncMode,
  CompetitionSyncStatus,
} from '../competition/competition.model';

interface MatrixCell {
  result: AthleteProfileResult;
}

interface MatrixRow {
  date: string;
  cells: Map<number, MatrixCell[]>;
}

interface ChartPoint {
  id: number;
  x: number;
  y: number;
  date: string;
  value: number;
  type: string;
}

interface ChartTick {
  y: number;
  value: number;
}

interface ChartScale {
  key: string;
  unit: string;
  axisX: number;
  ticks: ChartTick[];
  seriesTestIds: number[];
}

interface ChartSeries {
  testId: number;
  name: string;
  color: string;
  points: ChartPoint[];
  path: string;
}

interface ChartDateTick {
  x: number;
  label: string;
}

interface ProfileChart {
  scales: ChartScale[];
  series: ChartSeries[];
  dates: ChartDateTick[];
  gridLines: number[];
  plotLeft: number;
  plotRight: number;
  plotTop: number;
  plotBottom: number;
}

const chartColors = ['#2585d8', '#159c68', '#db8a2f', '#8558c7', '#d55252', '#259da5', '#b64f9a', '#777f31'];

@Component({
  imports: [
    AthleteFormModalComponent,
    ResultFormModalComponent,
    AthleteTrainingHistoryComponent,
    CompetitionResultFormComponent,
    ModalDialogDirective,
    RouterLink,
  ],
  selector: 'app-athlete-profile-page',
  styleUrl: './athlete-profile.page.scss',
  templateUrl: './athlete-profile.page.html',
})
export class AthleteProfilePage implements OnInit {
  athleteId = 0;
  readonly athlete = signal<AthleteResponse | null>(null);
  readonly tests = signal<TestResponse[]>([]);
  readonly profileLoading = signal(true);
  readonly profileError = signal<string | null>(null);
  readonly athleteOptions = signal<AthleteResponse[]>([]);
  readonly testOptions = signal<TestResponse[]>([]);
  readonly selectedTestIds = signal<number[]>([]);
  readonly fromDate = signal('');
  readonly toDate = signal('');
  readonly filterError = signal<string | null>(null);
  readonly history = signal<AthleteProfileResult[]>([]);
  readonly historyLoaded = signal(false);
  readonly historyLoading = signal(false);
  readonly historyError = signal<string | null>(null);
  readonly successMessage = signal<string | null>(null);
  readonly isAthleteFormOpen = signal(false);
  readonly isResultFormOpen = signal(false);
  readonly selectedResult = signal<ResultResponse | null>(null);
  readonly lookupLoading = signal(false);
  readonly lookupError = signal<string | null>(null);
  readonly selectedSeriesIds = signal<number[]>([]);
  readonly resultsExpanded = signal(false);
  readonly competitionResultsExpanded = signal(false);
  readonly competitionResults = signal<CompetitionResult[]>([]);
  readonly selectedCompetitionSeason = signal('');
  readonly selectedCompetitionEvent = signal('');
  readonly competitionSeasonOptions = computed(() => {
    const options = new Map<string, { key: string; season: number | null; type: CompetitionResult['seasonType'] }>();
    for (const result of this.competitionResults()) {
      const key = this.competitionSeasonKey(result);
      options.set(key, { key, season: result.season, type: result.seasonType });
    }
    return [...options.values()].sort((left, right) =>
      (right.season ?? -1) - (left.season ?? -1)
      || this.competitionSeasonLabel(left.type).localeCompare(this.competitionSeasonLabel(right.type), 'pl'));
  });
  readonly competitionEventOptions = computed(() => {
    const events = new Map<number, string>();
    for (const result of this.competitionResults()) events.set(result.eventId, result.eventName);
    return [...events.entries()]
      .map(([id, name]) => ({ id, name }))
      .sort((left, right) => left.name.localeCompare(right.name, 'pl') || left.id - right.id);
  });
  readonly filteredCompetitionResults = computed(() => this.competitionResults().filter((result) =>
    (!this.selectedCompetitionSeason() || this.competitionSeasonKey(result) === this.selectedCompetitionSeason())
    && (!this.selectedCompetitionEvent() || String(result.eventId) === this.selectedCompetitionEvent())));
  readonly competitionResultsLoading = signal(false);
  readonly competitionResultsError = signal<string | null>(null);
  readonly competitionSyncStatus = signal<CompetitionSyncStatus | null>(null);
  readonly competitionSyncLoading = signal(false);
  readonly competitionSyncError = signal<string | null>(null);
  readonly competitionSyncMode = signal<CompetitionSyncMode>('CURRENT_SEASON');
  readonly competitionResultFormOpen = signal(false);
  readonly selectedCompetitionResult = signal<CompetitionResult | null>(null);
  readonly deletingCompetitionResultId = signal<number | null>(null);
  readonly canManageCompetition = computed(() => this.auth.currentUser()?.role === 'ADMIN');
  readonly scaleGroupByTestId = signal<Map<number, number>>(new Map());
  readonly chart = computed(() => this.buildChart());
  readonly canEdit = computed(() => {
    const role = this.auth.currentUser()?.role;
    return role === 'ADMIN' || role === 'COACH';
  });
  readonly canOpenGeneralTrainings = computed(() => this.auth.currentUser()?.role === 'ATHLETE');
  readonly matrixRows = computed(() => {
    const rows = new Map<string, Map<number, MatrixCell[]>>();
    for (const result of this.history()) {
      let cells = rows.get(result.resultDate);
      if (!cells) {
        cells = new Map<number, MatrixCell[]>();
        rows.set(result.resultDate, cells);
      }
      const results = cells.get(result.testId) ?? [];
      results.push({ result });
      cells.set(result.testId, results);
    }
    return [...rows.entries()]
      .sort(([left], [right]) => left.localeCompare(right))
      .map(([date, cells]) => ({ date, cells }));
  });
  readonly selectedTests = computed(() => {
    const selected = this.selectedTestIds();
    return selected
      .map((id) => this.tests().find((test) => test.id === id))
      .filter((test): test is TestResponse => test !== undefined);
  });
  readonly testSearch = signal('');
  readonly visibleTestGroups = computed(() => {
    const query = this.testSearch().trim().toLocaleLowerCase('pl');
    const visible = this.tests().filter((test) => test.name.toLocaleLowerCase('pl').includes(query));
    return (['TIME', 'DISTANCE', 'SPEED'] as const)
      .map((category) => ({
        category,
        label: this.categoryLabel(category),
        tests: visible
          .filter((test) => test.category === category)
          .sort((left, right) => left.name.localeCompare(right.name, 'pl')),
      }))
      .filter((group) => group.tests.length > 0);
  });
  readonly allTestsSelected = computed(() => {
    const tests = this.visibleTestGroups().flatMap((group) => group.tests);
    const selected = this.selectedTestIds();
    return tests.length > 0 && tests.every((test) => selected.includes(test.id));
  });

  private readonly route = inject(ActivatedRoute);
  readonly auth = inject(AuthService);
  private readonly athletesApi = inject(AthletesApiService);
  private readonly testsApi = inject(TestsApiService);
  private readonly competitionApi = inject(CompetitionApiService);
  private readonly destroyRef = inject(DestroyRef);
  private profileRequestId = 0;
  private competitionRouteRequestId = 0;
  private competitionResultsRequestId = 0;
  private competitionSyncPoll: Subscription | null = null;
  private historyRequestId = 0;
  private appliedFilters: AthleteResultHistoryFilters | null = null;
  private athleteLookup: AthleteResponse[] | null = null;
  private testLookup: TestResponse[] | null = null;
  private loadedAthleteId = 0;
  private nextScaleGroupId = 1;

  ngOnInit(): void {
    this.route.paramMap
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((params) => {
        const idValue = params.get('id') ?? '';
        const id = /^\d+$/.test(idValue) ? Number(idValue) : 0;
        if (!Number.isSafeInteger(id) || id <= 0) {
          this.profileError.set('Nieprawidłowy identyfikator zawodnika.');
          this.profileLoading.set(false);
          return;
        }
        if (this.loadedAthleteId !== id) {
          this.invalidateCompetitionRequests();
          this.competitionResults.set([]);
          this.selectedCompetitionSeason.set('');
          this.selectedCompetitionEvent.set('');
          this.competitionSyncStatus.set(null);
          this.selectedTestIds.set([]);
          this.fromDate.set('');
          this.toDate.set('');
          this.resultsExpanded.set(false);
          this.competitionResultsExpanded.set(false);
          this.loadedAthleteId = id;
        }
        this.athleteId = id;
        this.loadProfile();
      });
  }

  genderLabel(gender: Gender): string {
    return gender === 'MALE' ? 'Mężczyzna' : 'Kobieta';
  }

  toggleResultsSection(): void {
    this.resultsExpanded.update((expanded) => !expanded);
  }

  toggleCompetitionResultsSection(): void {
    this.competitionResultsExpanded.update((expanded) => !expanded);
  }

  setTestSelected(testId: number, event: Event): void {
    const checkbox = event.target;
    if (!(checkbox instanceof HTMLInputElement)) return;
    this.selectedTestIds.update((ids) =>
      checkbox.checked
        ? ids.includes(testId) ? ids : [...ids, testId]
        : ids.filter((id) => id !== testId),
    );
    this.filterError.set(null);
  }

  toggleAllTests(): void {
    const visibleIds = this.visibleTestGroups().flatMap((group) => group.tests.map((test) => test.id));
    if (this.allTestsSelected()) {
      this.selectedTestIds.update((ids) => ids.filter((id) => !visibleIds.includes(id)));
    } else {
      const selected = this.selectedTestIds();
      this.selectedTestIds.set([...selected, ...visibleIds.filter((id) => !selected.includes(id))]);
    }
    this.filterError.set(null);
  }

  onTestSearch(event: Event): void {
    const input = event.target;
    if (input instanceof HTMLInputElement) this.testSearch.set(input.value);
  }

  categoryLabel(category: TestCategory): string {
    switch (category) {
      case 'TIME':
        return 'Czas';
      case 'DISTANCE':
        return 'Dystans';
      case 'SPEED':
        return 'Prędkość';
    }
  }

  onDateChange(event: Event, bound: 'from' | 'to'): void {
    const input = event.target;
    if (!(input instanceof HTMLInputElement)) return;
    if (bound === 'from') this.fromDate.set(input.value);
    else this.toDate.set(input.value);
    this.filterError.set(null);
  }

  loadResults(): void {
    const filters = this.currentFilters();
    if (!filters) return;
    this.fetchHistory(filters);
  }

  retryProfile(): void {
    this.loadProfile();
  }

  retryHistory(): void {
    if (this.appliedFilters) this.fetchHistory(this.appliedFilters);
    else this.loadResults();
  }

  openAthleteEdit(): void {
    if (this.canEdit() && this.athlete()) this.isAthleteFormOpen.set(true);
  }

  closeAthleteForm(): void {
    this.isAthleteFormOpen.set(false);
  }

  onAthleteSaved(athlete: AthleteResponse): void {
    this.athlete.set(athlete);
    this.isAthleteFormOpen.set(false);
    this.successMessage.set('Dane zawodnika zostały zapisane.');
  }

  setCompetitionSyncMode(event: Event): void {
    const select = event.target;
    if (!(select instanceof HTMLSelectElement)) return;
    if (select.value === 'CURRENT_SEASON' || select.value === 'FULL_HISTORY') {
      this.competitionSyncMode.set(select.value);
    }
  }

  startCompetitionSync(): void {
    const athlete = this.athlete();
    if (!this.canManageCompetition() || !athlete?.pzlaId || this.competitionSyncLoading()) return;
    this.competitionSyncError.set(null);
    this.competitionSyncLoading.set(true);
    this.competitionApi.startAthleteSync(this.athleteId, this.competitionSyncMode()).pipe(
      take(1),
      catchError((error: unknown) => {
        this.competitionSyncError.set(error instanceof HttpErrorResponse && error.status === 409
          ? 'Synchronizacja tego zawodnika już trwa.'
          : 'Nie udało się rozpocząć synchronizacji PZLA.');
        return EMPTY;
      }),
      finalize(() => this.competitionSyncLoading.set(false)),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe((status) => {
      this.competitionSyncStatus.set(status);
      if (status.runId !== null) this.watchCompetitionSync(status.runId);
    });
  }

  retryCompetitionData(): void {
    this.loadCompetitionData();
  }

  openCompetitionResultForm(result: CompetitionResult | null = null): void {
    if (!this.canManageCompetition()) return;
    this.selectedCompetitionResult.set(result);
    this.competitionResultFormOpen.set(true);
  }

  closeCompetitionResultForm(): void {
    this.competitionResultFormOpen.set(false);
    this.selectedCompetitionResult.set(null);
  }

  onCompetitionResultSaved(): void {
    this.closeCompetitionResultForm();
    this.successMessage.set('Wynik zawodów został zapisany.');
    this.loadCompetitionResults();
  }

  deleteCompetitionResult(result: CompetitionResult): void {
    if (!this.canManageCompetition() || this.deletingCompetitionResultId() !== null) return;
    if (!globalThis.confirm(`Czy na pewno usunąć wynik zawodów „${result.originalMark}” z dnia ${this.formatDate(result.resultDate)}?`)) {
      return;
    }
    this.deletingCompetitionResultId.set(result.id);
    this.competitionResultsError.set(null);
    this.competitionApi.deleteResult(result.id).pipe(
      take(1),
      catchError(() => {
        this.competitionResultsError.set('Nie udało się usunąć wyniku zawodów.');
        return EMPTY;
      }),
      finalize(() => this.deletingCompetitionResultId.set(null)),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe(() => {
      this.setCompetitionResults(this.competitionResults().filter((item) => item.id !== result.id));
      this.successMessage.set('Wynik zawodów został usunięty.');
    });
  }

  competitionSeasonLabel(type: CompetitionResult['seasonType']): string {
    if (type === 'INDOOR') return 'Hala';
    if (type === 'OUTDOOR') return 'Stadion';
    return 'Aktualny';
  }

  competitionSeasonKey(result: CompetitionResult): string {
    return `${result.season ?? 'unknown'}:${result.seasonType}`;
  }

  setCompetitionFilter(event: Event, filter: 'season' | 'event'): void {
    const select = event.target;
    if (!(select instanceof HTMLSelectElement)) return;
    if (filter === 'season') this.selectedCompetitionSeason.set(select.value);
    else this.selectedCompetitionEvent.set(select.value);
  }

  clearCompetitionFilters(): void {
    this.selectedCompetitionSeason.set('');
    this.selectedCompetitionEvent.set('');
  }

  private setCompetitionResults(results: CompetitionResult[]): void {
    this.competitionResults.set(results);
    if (this.selectedCompetitionSeason()
        && !results.some((result) => this.competitionSeasonKey(result) === this.selectedCompetitionSeason())) {
      this.selectedCompetitionSeason.set('');
    }
    if (this.selectedCompetitionEvent()
        && !results.some((result) => String(result.eventId) === this.selectedCompetitionEvent())) {
      this.selectedCompetitionEvent.set('');
    }
  }

  competitionSourceLabel(source: CompetitionResult['source']): string {
    return source === 'PZLA' ? 'PZLA' : 'TFHub';
  }

  formatWind(wind: number): string {
    return `${new Intl.NumberFormat('pl-PL', { minimumFractionDigits: 1, maximumFractionDigits: 2 }).format(wind)} m/s`;
  }

  competitionSyncStatusLabel(status: CompetitionSyncStatus['status']): string {
    switch (status) {
      case 'QUEUED': return 'Oczekuje';
      case 'RUNNING': return 'W trakcie';
      case 'SUCCEEDED': return 'Zakończona powodzeniem';
      case 'FAILED': return 'Niepowodzenie';
      case 'NOT_SYNCHRONIZED': return 'Jeszcze nie synchronizowano';
    }
  }

  competitionDateTime(value: string | null): string {
    return value
      ? new Intl.DateTimeFormat('pl-PL', {
        dateStyle: 'medium',
        timeStyle: 'short',
        timeZone: 'Europe/Warsaw',
      }).format(new Date(value))
      : '—';
  }

  openAddResult(): void {
    if (!this.canEdit()) return;
    if (!this.athlete()?.active) {
      this.lookupError.set('Nie można dodać wyniku zawodnikowi nieaktywnemu.');
      return;
    }
    this.lookupError.set(null);
    if (this.athleteLookup && this.testLookup) {
      this.isResultFormOpen.set(true);
      return;
    }

    this.lookupLoading.set(true);
    forkJoin({
      athletes: this.athletesApi.getAll(),
      tests: this.testsApi.getAll(),
    })
      .pipe(
        take(1),
        catchError((error: unknown) => {
          this.lookupError.set(this.getErrorMessage(error, 'lookup'));
          return EMPTY;
        }),
        finalize(() => this.lookupLoading.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe(({ athletes, tests }) => {
        this.athleteLookup = athletes;
        this.testLookup = tests;
        this.athleteOptions.set(athletes);
        this.testOptions.set(tests);
        this.isResultFormOpen.set(true);
      });
  }

  closeResultForm(): void {
    this.isResultFormOpen.set(false);
    this.selectedResult.set(null);
  }

  openResultEdit(result: AthleteProfileResult): void {
    if (!this.canEdit()) return;
    this.selectedResult.set({
      id: result.id,
      athleteId: this.athleteId,
      testId: result.testId,
      resultDate: result.resultDate,
      value: result.value,
      type: result.type,
    });
    this.ensureEditLookups();
  }

  onResultClick(event: MouseEvent, result: AthleteProfileResult): void {
    if (this.canEdit() && event.detail === 0) this.openResultEdit(result);
  }

  onResultPointerUp(event: PointerEvent, result: AthleteProfileResult): void {
    if (this.canEdit() && event.pointerType === 'touch') this.openResultEdit(result);
  }

  onResultSaved(_result: ResultResponse): void {
    this.isResultFormOpen.set(false);
    this.selectedResult.set(null);
    this.successMessage.set('Wynik został zapisany.');
    this.refreshTestsAndHistory();
  }

  toggleSeries(testId: number): void {
    this.selectedSeriesIds.update((ids) =>
      ids.includes(testId) ? ids.filter((id) => id !== testId) : [...ids, testId],
    );
  }

  separateSelectedScales(): void {
    const selected = this.selectedSeriesIds();
    if (selected.length === 0) return;
    const groupId = this.nextScaleGroupId++;
    this.scaleGroupByTestId.update((groups) => {
      const nextGroups = new Map(groups);
      for (const testId of selected) nextGroups.set(testId, groupId);
      return nextGroups;
    });
    this.selectedSeriesIds.set([]);
  }

  mergeSelectedScales(): void {
    const selected = this.selectedSeriesIds();
    if (selected.length === 0) return;
    this.scaleGroupByTestId.update((groups) => {
      const nextGroups = new Map(groups);
      for (const testId of selected) nextGroups.delete(testId);
      return nextGroups;
    });
    this.selectedSeriesIds.set([]);
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

  typeLabel(type: string): string {
    return type === 'TRAINING' ? 'Trening' : 'Zawody';
  }

  private loadProfile(): void {
    const requestId = ++this.profileRequestId;
    this.profileLoading.set(true);
    this.profileError.set(null);
    this.athlete.set(null);
    this.tests.set([]);
    this.history.set([]);
    this.historyLoaded.set(false);
    this.appliedFilters = null;

    forkJoin({
      athlete: this.athletesApi.getById(this.athleteId),
      tests: this.athletesApi.getTestsWithResults(this.athleteId),
    })
      .pipe(
        take(1),
        catchError((error: unknown) => {
          if (requestId === this.profileRequestId) {
            this.profileError.set(this.getErrorMessage(error, 'profile'));
          }
          return EMPTY;
        }),
        finalize(() => {
          if (requestId === this.profileRequestId) this.profileLoading.set(false);
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe(({ athlete, tests }) => {
        if (requestId !== this.profileRequestId) return;
        this.athlete.set(athlete);
        this.tests.set(tests);
        const existingIds = new Set(tests.map((test) => test.id));
        const preservedSelection = this.selectedTestIds().filter((id) => existingIds.has(id));
        this.selectedTestIds.set(preservedSelection.length > 0
          ? preservedSelection
          : tests.map((test) => test.id));
        this.loadCompetitionData();
      });
  }

  private loadCompetitionData(): void {
    this.loadCompetitionResults();
    this.loadCompetitionSyncStatus(this.competitionRouteRequestId);
  }

  private loadCompetitionResults(): void {
    const routeRequestId = this.competitionRouteRequestId;
    const resultsRequestId = ++this.competitionResultsRequestId;
    this.competitionResultsLoading.set(true);
    this.competitionResultsError.set(null);
    this.competitionApi.getAthleteResults(this.athleteId).pipe(
      take(1),
      catchError((error: unknown) => {
        if (routeRequestId === this.competitionRouteRequestId
            && resultsRequestId === this.competitionResultsRequestId) {
          this.competitionResultsError.set(error instanceof HttpErrorResponse && error.status === 403
            ? 'Brak dostępu do wyników zawodów tego zawodnika.'
            : 'Nie udało się pobrać wyników zawodów.');
        }
        return EMPTY;
      }),
      finalize(() => {
        if (routeRequestId === this.competitionRouteRequestId
            && resultsRequestId === this.competitionResultsRequestId) this.competitionResultsLoading.set(false);
      }),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe((results) => {
      if (routeRequestId === this.competitionRouteRequestId
          && resultsRequestId === this.competitionResultsRequestId) this.setCompetitionResults(results);
    });
  }

  private loadCompetitionSyncStatus(routeRequestId: number): void {
    this.competitionApi.getAthleteSyncStatus(this.athleteId).pipe(
      take(1),
      catchError((error: unknown) => {
        if (routeRequestId === this.competitionRouteRequestId) {
          this.competitionSyncError.set(error instanceof HttpErrorResponse && error.status === 403
            ? 'Brak dostępu do statusu synchronizacji.'
            : 'Nie udało się pobrać statusu synchronizacji.');
        }
        return EMPTY;
      }),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe((status) => {
      if (routeRequestId !== this.competitionRouteRequestId) return;
      this.competitionSyncStatus.set(status);
      if ((status.status === 'QUEUED' || status.status === 'RUNNING') && status.runId !== null) {
        this.watchCompetitionSync(status.runId);
      }
    });
  }

  private watchCompetitionSync(runId: number): void {
    this.competitionSyncPoll?.unsubscribe();
    const routeRequestId = this.competitionRouteRequestId;
    this.competitionSyncLoading.set(true);
    this.competitionSyncPoll = interval(3000).pipe(
      switchMap(() => this.competitionApi.getSyncStatus(runId)),
      takeWhile((status) => status.status === 'QUEUED' || status.status === 'RUNNING', true),
      catchError(() => {
        if (routeRequestId === this.competitionRouteRequestId) {
          this.competitionSyncError.set('Nie udało się odświeżyć statusu synchronizacji.');
        }
        return EMPTY;
      }),
      finalize(() => {
        if (routeRequestId === this.competitionRouteRequestId) this.competitionSyncLoading.set(false);
      }),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe((status) => {
      if (routeRequestId !== this.competitionRouteRequestId) return;
      this.competitionSyncStatus.set(status);
      if (status.status !== 'QUEUED' && status.status !== 'RUNNING') {
        this.competitionSyncLoading.set(false);
        this.loadCompetitionData();
      }
    });
  }

  private invalidateCompetitionRequests(): void {
    this.competitionRouteRequestId++;
    this.competitionResultsRequestId++;
    this.competitionSyncError.set(null);
    this.competitionResultsError.set(null);
    this.competitionSyncPoll?.unsubscribe();
    this.competitionSyncPoll = null;
    this.competitionSyncLoading.set(false);
  }

  private currentFilters(): AthleteResultHistoryFilters | null {
    const fromDate = this.fromDate();
    const toDate = this.toDate();
    if (fromDate && toDate && fromDate > toDate) {
      this.filterError.set('Data początkowa nie może być późniejsza niż data końcowa.');
      return null;
    }
    const testIds = this.selectedTestIds();
    if (testIds.length === 0) {
      this.filterError.set('Wybierz co najmniej jeden test.');
      return null;
    }
    this.filterError.set(null);
    return {
      testIds: [...testIds],
      ...(fromDate ? { fromDate } : {}),
      ...(toDate ? { toDate } : {}),
    };
  }

  private fetchHistory(filters: AthleteResultHistoryFilters): void {
    const requestId = ++this.historyRequestId;
    const requestFilters = {
      ...filters,
      testIds: [...filters.testIds],
    };
    this.historyLoading.set(true);
    this.historyError.set(null);
    this.successMessage.set(null);
    this.athletesApi
      .getResultHistory(this.athleteId, requestFilters)
      .pipe(
        take(1),
        catchError((error: unknown) => {
          if (requestId === this.historyRequestId) {
            this.historyError.set(this.getErrorMessage(error, 'history'));
          }
          return EMPTY;
        }),
        finalize(() => {
          if (requestId === this.historyRequestId) this.historyLoading.set(false);
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((results) => {
        if (requestId !== this.historyRequestId) return;
        this.appliedFilters = requestFilters;
        this.history.set([...results].sort((left, right) =>
          left.resultDate.localeCompare(right.resultDate) ||
          left.testId - right.testId ||
          left.id - right.id,
        ));
        this.historyLoaded.set(true);
        this.historyError.set(null);
      });
  }

  private ensureEditLookups(): void {
    if (this.athleteLookup && this.testLookup) {
      this.isResultFormOpen.set(true);
      return;
    }
    this.lookupLoading.set(true);
    forkJoin({
      athletes: this.athletesApi.getAll(),
      tests: this.testsApi.getAll(),
    })
      .pipe(
        take(1),
        catchError((error: unknown) => {
          this.lookupError.set(this.getErrorMessage(error, 'lookup'));
          this.selectedResult.set(null);
          return EMPTY;
        }),
        finalize(() => this.lookupLoading.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe(({ athletes, tests }) => {
        this.athleteLookup = athletes;
        this.testLookup = tests;
        this.athleteOptions.set(athletes);
        this.testOptions.set(tests);
        this.isResultFormOpen.set(true);
      });
  }

  private refreshTestsAndHistory(): void {
    const currentSelection = [...this.selectedTestIds()];
    this.athletesApi
      .getTestsWithResults(this.athleteId)
      .pipe(
        take(1),
        catchError((error: unknown) => {
          this.lookupError.set(this.getErrorMessage(error, 'tests'));
          return EMPTY;
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((tests) => {
        this.tests.set(tests);
        const available = new Set(tests.map((test) => test.id));
        this.selectedTestIds.set(currentSelection.filter((id) => available.has(id)));
        if (this.historyLoaded() && this.appliedFilters) {
          this.fetchHistory(this.appliedFilters);
        }
      });
  }

  private buildChart(): ProfileChart | null {
    const results = this.history();
    const selectedTests = this.selectedTests();
    if (results.length === 0 || selectedTests.length === 0) return null;

    const testIdsWithResults = new Set(results.map((result) => result.testId));
    const seriesTests = selectedTests.filter((test) => testIdsWithResults.has(test.id));
    if (seriesTests.length === 0) return null;

    const scaleGroups = this.scaleGroupByTestId();
    const grouped = new Map<string, { unit: string; tests: TestResponse[] }>();
    for (const test of seriesTests) {
      const key = `${test.unit}:${scaleGroups.get(test.id) ?? 'master'}`;
      const group = grouped.get(key) ?? { unit: test.unit, tests: [] };
      group.tests.push(test);
      grouped.set(key, group);
    }
    const groups = [...grouped.entries()];
    const plotLeft = 88 + (groups.length - 1) * 52;
    const plotRight = 954;
    const plotTop = 42;
    const plotBottom = 340;
    const dates = [...new Set(results.map((result) => result.resultDate))].sort();
    const minDate = Date.parse(`${dates[0]}T00:00:00Z`);
    const maxDate = Date.parse(`${dates[dates.length - 1]}T00:00:00Z`);
    const xForDate = (date: string) => maxDate === minDate
      ? (plotLeft + plotRight) / 2
      : plotLeft + (Date.parse(`${date}T00:00:00Z`) - minDate) / (maxDate - minDate) * (plotRight - plotLeft);

    const scales: ChartScale[] = groups.map(([key, group], index) => {
      const values = results.filter((result) => group.tests.some((test) => test.id === result.testId))
        .map((result) => result.value);
      const range = this.valueRange(values);
      const axisX = plotLeft - index * 52;
      return {
        key,
        unit: group.unit,
        axisX,
        seriesTestIds: group.tests.map((test) => test.id),
        ticks: Array.from({ length: 5 }, (_, tick) => {
          const ratio = tick / 4;
          const value = range.max - ratio * (range.max - range.min);
          return {
            y: plotTop + ratio * (plotBottom - plotTop),
            value,
          };
        }),
      };
    });

    const series: ChartSeries[] = seriesTests.map((test) => {
      const scaleIndex = groups.findIndex(([, group]) => group.tests.some((item) => item.id === test.id));
      const scale = scales[scaleIndex];
      const scaleMin = Math.min(...scale.ticks.map((tick) => tick.value));
      const scaleMax = Math.max(...scale.ticks.map((tick) => tick.value));
      const testResults = results.filter((result) => result.testId === test.id);
      const pointY = (value: number) =>
        plotBottom - (value - scaleMin) / (scaleMax - scaleMin) * (plotBottom - plotTop);
      const points: ChartPoint[] = testResults.map((result) => ({
        id: result.id,
        x: xForDate(result.resultDate),
        y: pointY(result.value),
        date: result.resultDate,
        value: result.value,
        type: result.type,
      }));
      const firstPointForDate = new Map<string, ChartPoint>();
      for (const point of points) {
        if (!firstPointForDate.has(point.date)) firstPointForDate.set(point.date, point);
      }
      const path = [...firstPointForDate.values()]
        .map((point, index) => `${index === 0 ? 'M' : 'L'} ${point.x} ${point.y}`)
        .join(' ');
      return {
        testId: test.id,
        name: test.name,
        color: chartColors[seriesTests.indexOf(test) % chartColors.length],
        points,
        path,
      };
    });

    const dateIndices = dates.length <= 6
      ? dates.map((_, index) => index)
      : Array.from({ length: 6 }, (_, index) => Math.round(index * (dates.length - 1) / 5));

    return {
      scales,
      series,
      dates: [...new Set(dateIndices)].map((index) => ({
        x: xForDate(dates[index]),
        label: this.formatDate(dates[index]).replace(/\.\d{4}$/, ''),
      })),
      gridLines: scales[0].ticks.map((tick) => tick.y),
      plotLeft,
      plotRight,
      plotTop,
      plotBottom,
    };
  }

  private valueRange(values: number[]): { min: number; max: number } {
    const minValue = Math.min(...values);
    const maxValue = Math.max(...values);
    const rawRange = maxValue - minValue;
    const padding = rawRange === 0 ? Math.max(Math.abs(minValue) * 0.05, 0.1) : rawRange * 0.12;
    const min = minValue - padding;
    const max = maxValue + padding;
    const step = this.niceStep((max - min) / 4);
    const roundedMin = Math.floor(min / step) * step;
    const roundedMax = Math.ceil(max / step) * step;
    return roundedMin === roundedMax
      ? { min: roundedMin - step, max: roundedMax + step }
      : { min: roundedMin, max: roundedMax };
  }

  private niceStep(value: number): number {
    const magnitude = 10 ** Math.floor(Math.log10(Math.abs(value) || 1));
    const normalized = value / magnitude;
    const rounded = normalized <= 1 ? 1 : normalized <= 2 ? 2 : normalized <= 2.5 ? 2.5 : normalized <= 5 ? 5 : 10;
    return rounded * magnitude;
  }

  private getErrorMessage(error: unknown, request: 'profile' | 'history' | 'tests' | 'lookup'): string {
    if (error instanceof HttpErrorResponse && error.status === 401) {
      return 'Sesja wygasła lub użytkownik nie jest zalogowany.';
    }
    if (error instanceof HttpErrorResponse && error.status === 403) {
      return 'Brak dostępu do danych zawodnika.';
    }
    if (request === 'profile' && error instanceof HttpErrorResponse && error.status === 404) {
      return 'Nie znaleziono zawodnika.';
    }
    if (request === 'lookup') {
      return 'Nie udało się przygotować formularza wyniku. Spróbuj ponownie.';
    }
    if (request === 'tests') {
      return 'Nie udało się odświeżyć listy testów zawodnika.';
    }
    return request === 'history'
      ? 'Nie udało się pobrać historii wyników. Spróbuj ponownie.'
      : 'Nie udało się pobrać profilu zawodnika. Spróbuj ponownie.';
  }
}

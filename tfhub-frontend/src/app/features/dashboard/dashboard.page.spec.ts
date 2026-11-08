import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, Subject, throwError } from 'rxjs';
import { AthletesApiService } from '../athletes/athletes-api.service';
import { TestsApiService } from '../tests/tests-api.service';
import { DashboardPage } from './dashboard.page';
import { StatisticsApiService } from './statistics-api.service';
import { ClubRecords, DashboardOverview } from './statistics.model';

const overview: DashboardOverview = {
  activeAthleteCount: 3,
  availableTests: [
    { id: 8, name: 'Bieg 100 m', category: 'TIME', unit: 's', active: false },
    { id: 9, name: 'Skok w dal', category: 'DISTANCE', unit: 'm', active: true },
  ],
  availableSeasons: [2025, 2024],
  recentResults: [
    {
      id: 44,
      resultDate: '2025-06-12',
      athleteId: 5,
      athleteName: 'Anna Kowalska',
      testId: 8,
      testName: 'Bieg 100 m',
      value: 12.34,
      unit: 's',
      type: 'COMPETITION',
    },
  ],
};

const records = (testId: number): ClubRecords => ({
  testId,
  season: null,
  FEMALE: [{
    place: 1,
    athleteId: 5,
    athleteName: 'Anna Kowalska',
    value: 12.34,
    unit: 's',
    resultDate: '2025-06-12',
  }],
  MALE: [],
});

describe('DashboardPage', () => {
  let statistics: {
    getDashboardOverview: ReturnType<typeof vi.fn>;
    getClubRecords: ReturnType<typeof vi.fn>;
  };
  let athletesApi: { getAll: ReturnType<typeof vi.fn> };
  let testsApi: { getAll: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    statistics = {
      getDashboardOverview: vi.fn(() => of(overview)),
      getClubRecords: vi.fn((testId: number) => of(records(testId))),
    };
    athletesApi = { getAll: vi.fn(() => of([])) };
    testsApi = { getAll: vi.fn(() => of([])) };
    TestBed.configureTestingModule({
      imports: [DashboardPage],
      providers: [
        { provide: StatisticsApiService, useValue: statistics },
        { provide: AthletesApiService, useValue: athletesApi },
        { provide: TestsApiService, useValue: testsApi },
        provideRouter([]),
      ],
    });
  });

  it('loads the overview, selects its first test and renders returned values', () => {
    const fixture = TestBed.createComponent(DashboardPage);
    fixture.detectChanges();

    expect(statistics.getDashboardOverview).toHaveBeenCalledTimes(1);
    expect(statistics.getClubRecords).toHaveBeenCalledWith(8, null);
    expect(fixture.nativeElement.textContent).toContain('Aktywni zawodnicy');
    expect(fixture.nativeElement.textContent).toContain('Anna Kowalska');
    expect(fixture.nativeElement.querySelectorAll('.record-card table thead th').length).toBe(8);
    expect(fixture.nativeElement.querySelector('app-result-form-modal')).toBeNull();
  });

  it('requests records with the selected test and season, omitting season for all time', () => {
    const fixture = TestBed.createComponent(DashboardPage);
    fixture.detectChanges();

    const testSelect = fixture.nativeElement.querySelector('select') as HTMLSelectElement;
    testSelect.value = '9';
    testSelect.dispatchEvent(new Event('change'));
    fixture.detectChanges();
    expect(statistics.getClubRecords).toHaveBeenLastCalledWith(9, null);

    const seasonSelect = fixture.nativeElement.querySelectorAll('select')[1] as HTMLSelectElement;
    seasonSelect.value = '2024';
    seasonSelect.dispatchEvent(new Event('change'));
    fixture.detectChanges();
    expect(statistics.getClubRecords).toHaveBeenLastCalledWith(9, 2024);
  });

  it('preserves valid test and season selections when refreshing the overview', () => {
    const fixture = TestBed.createComponent(DashboardPage);
    fixture.detectChanges();
    fixture.componentInstance.selectedTestId.set('9');
    fixture.componentInstance.selectedSeason.set('2024');

    fixture.componentInstance.refreshOverview();

    expect(fixture.componentInstance.selectedTestId()).toBe('9');
    expect(fixture.componentInstance.selectedSeason()).toBe('2024');
    expect(statistics.getClubRecords).toHaveBeenLastCalledWith(9, 2024);
  });

  it('ignores stale record responses after the selection changes', () => {
    const first = new Subject<ClubRecords>();
    const latest = new Subject<ClubRecords>();
    statistics.getClubRecords
      .mockReturnValueOnce(first)
      .mockReturnValueOnce(latest);
    const fixture = TestBed.createComponent(DashboardPage);
    fixture.detectChanges();

    const testSelect = fixture.nativeElement.querySelector('select') as HTMLSelectElement;
    testSelect.value = '9';
    testSelect.dispatchEvent(new Event('change'));
    latest.next(records(9));
    first.next(records(8));
    fixture.detectChanges();

    expect(fixture.componentInstance.records()?.testId).toBe(9);
  });

  it('does not request records when no tests are available', () => {
    statistics.getDashboardOverview.mockReturnValue(of({
      activeAthleteCount: 0,
      availableTests: [],
      availableSeasons: [],
      recentResults: [],
    }));
    const fixture = TestBed.createComponent(DashboardPage);
    fixture.detectChanges();
    expect(statistics.getClubRecords).not.toHaveBeenCalled();
    expect(fixture.nativeElement.textContent).toContain('Brak testów z zarejestrowanymi wynikami.');
  });

  it('shows a useful error when records cannot be loaded and allows retry', () => {
    statistics.getClubRecords.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 500 })),
    );
    const fixture = TestBed.createComponent(DashboardPage);
    fixture.detectChanges();
    expect(fixture.componentInstance.recordsError()).toContain('Nie udało się pobrać rekordów');
    expect(fixture.componentInstance.records()).toBeNull();
    fixture.componentInstance.retryRecords();
    expect(statistics.getClubRecords).toHaveBeenCalledTimes(2);
  });

  it('opens the existing athlete form without requesting result lookups', () => {
    const fixture = TestBed.createComponent(DashboardPage);
    fixture.detectChanges();
    fixture.componentInstance.openAthleteForm();
    fixture.detectChanges();
    expect(fixture.componentInstance.isAthleteFormOpen()).toBe(true);
    expect(athletesApi.getAll).not.toHaveBeenCalled();
    expect(testsApi.getAll).not.toHaveBeenCalled();
    expect(fixture.nativeElement.querySelector('app-athlete-form-modal')).toBeTruthy();
  });

  it('loads athlete and test choices once and opens the existing result forms', () => {
    const fixture = TestBed.createComponent(DashboardPage);
    fixture.detectChanges();

    fixture.componentInstance.openResultForm();
    fixture.detectChanges();
    expect(athletesApi.getAll).toHaveBeenCalledTimes(1);
    expect(testsApi.getAll).toHaveBeenCalledTimes(1);
    expect(fixture.componentInstance.isResultFormOpen()).toBe(true);

    fixture.componentInstance.closeResultForm();
    fixture.componentInstance.openMultipleResultForm();
    fixture.detectChanges();
    expect(athletesApi.getAll).toHaveBeenCalledTimes(1);
    expect(testsApi.getAll).toHaveBeenCalledTimes(1);
    expect(fixture.componentInstance.isMultipleResultFormOpen()).toBe(true);
  });

  it('refreshes overview and records after successful result creation', () => {
    const fixture = TestBed.createComponent(DashboardPage);
    fixture.detectChanges();
    fixture.componentInstance.onResultCreated({
      id: 45,
      athleteId: 5,
      testId: 8,
      resultDate: '2025-06-13',
      value: 12.2,
      type: 'TRAINING',
    });
    expect(statistics.getDashboardOverview).toHaveBeenCalledTimes(2);
    expect(statistics.getClubRecords).toHaveBeenCalledTimes(2);
  });
});

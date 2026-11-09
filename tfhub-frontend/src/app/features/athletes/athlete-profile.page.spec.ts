import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { ActivatedRoute, provideRouter } from '@angular/router';
import { convertToParamMap } from '@angular/router';
import { Observable, Subject, of, throwError } from 'rxjs';
import { signal } from '@angular/core';
import { AthleteResponse } from './athlete.model';
import { AthleteProfileResult } from './athlete-profile.model';
import { AthletesApiService } from './athletes-api.service';
import { AthleteProfilePage } from './athlete-profile.page';
import { ResultFormModalComponent } from '../results/result-form-modal.component';
import { TestsApiService } from '../tests/tests-api.service';
import { AuthService } from '../../core/auth/auth.service';

const athlete: AthleteResponse = {
  id: 8,
  firstName: 'Jan',
  lastName: 'Kowalski',
  birthYear: 2010,
  gender: 'MALE',
  active: true,
};

const inactiveAthlete: AthleteResponse = { ...athlete, active: false };

const tests = [
  { id: 3, name: 'Bieg 100 m', unit: 's', category: 'TIME' as const, higherIsBetter: false, active: true },
  { id: 5, name: 'Skok w dal', unit: 'm', category: 'DISTANCE' as const, higherIsBetter: true, active: false },
];

const results: AthleteProfileResult[] = [
  { id: 10, testId: 3, testName: 'Bieg 100 m', testUnit: 's', resultDate: '2024-05-01', value: 12.1, type: 'TRAINING' },
  { id: 11, testId: 3, testName: 'Bieg 100 m', testUnit: 's', resultDate: '2024-05-01', value: 11.9, type: 'COMPETITION' },
  { id: 12, testId: 5, testName: 'Skok w dal', testUnit: 'm', resultDate: '2024-08-01', value: 5.2, type: 'COMPETITION' },
];

describe('AthleteProfilePage', () => {
  let fixture: ComponentFixture<AthleteProfilePage>;
  let athletesApi: {
    getById: ReturnType<typeof vi.fn>;
    getTestsWithResults: ReturnType<typeof vi.fn>;
    getResultHistory: ReturnType<typeof vi.fn>;
    getAll: ReturnType<typeof vi.fn>;
    updateAthlete: ReturnType<typeof vi.fn>;
  };
  let testsApi: { getAll: ReturnType<typeof vi.fn> };

  async function createPage(
    requestedAthlete: AthleteResponse = athlete,
    historyRequest: () => Observable<AthleteProfileResult[]> = () => of(results),
  ): Promise<void> {
    athletesApi = {
      getById: vi.fn(() => of(requestedAthlete)),
      getTestsWithResults: vi.fn(() => of(tests)),
      getResultHistory: vi.fn(historyRequest),
      getAll: vi.fn(() => of([athlete])),
      updateAthlete: vi.fn(),
    };
    testsApi = { getAll: vi.fn(() => of(tests)) };
    await TestBed.configureTestingModule({
      imports: [AthleteProfilePage],
      providers: [
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { paramMap: of(convertToParamMap({ id: '8' })) } },
        { provide: AthletesApiService, useValue: athletesApi },
        { provide: TestsApiService, useValue: testsApi },
        {
          provide: AuthService,
          useValue: {
            currentUser: signal({ authenticated: true, userId: 1, username: 'admin', role: 'ADMIN', athleteId: null }),
          },
        },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(AthleteProfilePage);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  it('loads the athlete and includes their active state and profile details', async () => {
    await createPage();
    expect(athletesApi.getById).toHaveBeenCalledWith(8);
    expect(athletesApi.getTestsWithResults).toHaveBeenCalledWith(8);
    expect(fixture.nativeElement.textContent).toContain('Jan Kowalski');
    expect(fixture.nativeElement.textContent).toContain('Aktywny');
    expect(fixture.nativeElement.textContent).toContain('Rocznik 2010');
    expect(athletesApi.getResultHistory).not.toHaveBeenCalled();
    expect(fixture.nativeElement.querySelector('.history-card')).toBeNull();
    expect(fixture.nativeElement.querySelector('.chart-card')).toBeNull();
  });

  it('shows results and chart only after the first request succeeds', async () => {
    const response = new Subject<AthleteProfileResult[]>();
    await createPage(athlete, () => response);
    (fixture.nativeElement.querySelector('.load-button') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.history-card')).toBeNull();
    expect(fixture.nativeElement.querySelector('.chart-card')).toBeNull();
    expect(fixture.nativeElement.textContent).toContain('Pobieranie historii wyników');
    response.next(results);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.history-card')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('.chart-card')).not.toBeNull();
  });

  it('shows empty states after a successful empty response', async () => {
    await createPage(athlete, () => of([]));
    (fixture.nativeElement.querySelector('.load-button') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.history-card')?.textContent).toContain('Brak wyników dla wybranych filtrów.');
    expect(fixture.nativeElement.querySelector('.chart-card')?.textContent).toContain('Brak wyników do przedstawienia na wykresie.');
  });

  it('shows the initial request error and allows retry without displaying the sections', async () => {
    await createPage(athlete, () => throwError(() => new HttpErrorResponse({ status: 500 })));
    (fixture.nativeElement.querySelector('.load-button') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.history-card')).toBeNull();
    expect(fixture.nativeElement.querySelector('.chart-card')).toBeNull();
    expect(fixture.nativeElement.textContent).toContain('Nie udało się pobrać historii wyników.');
    athletesApi.getResultHistory.mockReturnValueOnce(of(results));
    (fixture.nativeElement.querySelector('.feedback.error .button') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.history-card')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('.chart-card')).not.toBeNull();
  });

  it('loads and displays inactive athlete data without allowing result creation', async () => {
    await createPage(inactiveAthlete);
    expect(fixture.nativeElement.textContent).toContain('Nieaktywny');
    expect((fixture.nativeElement.querySelector('.athlete-actions .button--primary') as HTMLButtonElement).disabled).toBe(true);
  });

  it('loads selected tests and date filters only after explicit request, retaining duplicate same-cell results', async () => {
    await createPage();
    const checkboxes = fixture.nativeElement.querySelectorAll('.test-option input') as NodeListOf<HTMLInputElement>;
    checkboxes[0].checked = true;
    checkboxes[0].dispatchEvent(new Event('change'));
    checkboxes[1].checked = true;
    checkboxes[1].dispatchEvent(new Event('change'));
    const from = fixture.nativeElement.querySelector('input[type="date"]') as HTMLInputElement;
    from.value = '2024-01-01';
    from.dispatchEvent(new Event('input'));
    const to = fixture.nativeElement.querySelectorAll('input[type="date"]')[1] as HTMLInputElement;
    to.value = '2024-12-31';
    to.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    (fixture.nativeElement.querySelector('.load-button') as HTMLButtonElement).click();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(athletesApi.getResultHistory).toHaveBeenCalledWith(8, {
      testIds: [3, 5],
      fromDate: '2024-01-01',
      toDate: '2024-12-31',
    });
    expect(fixture.nativeElement.querySelectorAll('.matrix tbody tr')).toHaveLength(2);
    expect(fixture.nativeElement.querySelectorAll('.matrix tbody tr:first-child .result-value')).toHaveLength(2);
    expect(fixture.nativeElement.textContent).toContain('Trening');
    expect(fixture.nativeElement.textContent).toContain('Zawody');
    expect(fixture.nativeElement.querySelectorAll('.matrix tbody tr .result-value')).toHaveLength(3);
  });

  it('toggles all tests without loading results and preserves selection order when completing a partial selection', async () => {
    await createPage();
    const component = fixture.componentInstance;
    const button = fixture.nativeElement.querySelector('.select-all-button') as HTMLButtonElement;
    const checkboxes = fixture.nativeElement.querySelectorAll('.test-option input') as NodeListOf<HTMLInputElement>;

    expect(button.previousElementSibling?.classList.contains('test-options')).toBe(true);
    expect(button.textContent).toContain('Odznacz wszystkie');
    button.click();
    fixture.detectChanges();
    expect(component.selectedTestIds()).toEqual([]);
    expect([...checkboxes].every((checkbox) => !checkbox.checked)).toBe(true);
    expect(button.textContent).toContain('Zaznacz wszystkie');

    component.loadResults();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Wybierz co najmniej jeden test.');
    button.click();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).not.toContain('Wybierz co najmniej jeden test.');
    checkboxes[0].checked = false;
    checkboxes[0].dispatchEvent(new Event('change'));
    fixture.detectChanges();
    expect(button.textContent).toContain('Zaznacz wszystkie');
    button.click();
    fixture.detectChanges();
    expect(component.selectedTestIds()).toEqual([5, 3]);
    expect([...checkboxes].every((checkbox) => checkbox.checked)).toBe(true);
    expect(button.textContent).toContain('Odznacz wszystkie');
    expect(fixture.nativeElement.textContent).not.toContain('Wybierz co najmniej jeden test.');
    expect(athletesApi.getResultHistory).not.toHaveBeenCalled();

    (fixture.nativeElement.querySelector('.load-button') as HTMLButtonElement).click();
    expect(athletesApi.getResultHistory).toHaveBeenCalledWith(8, { testIds: [5, 3] });
  });

  it('groups tests by category and filters them by name, limiting select-all to the visible tests', async () => {
    await createPage();
    const component = fixture.componentInstance;
    const titles = () => [...fixture.nativeElement.querySelectorAll('.test-group__title')]
      .map((title) => (title as HTMLElement).textContent?.trim());
    expect(titles()).toEqual(['Czas', 'Dystans']);

    const search = fixture.nativeElement.querySelector('.test-search') as HTMLInputElement;
    search.value = 'skok';
    search.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    expect(titles()).toEqual(['Dystans']);

    const button = fixture.nativeElement.querySelector('.select-all-button') as HTMLButtonElement;
    button.click();
    fixture.detectChanges();
    expect(component.selectedTestIds()).toEqual([3]);

    search.value = 'brak';
    search.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Brak testów pasujących do wyszukiwania.');
  });

  it('does not show the select-all action when there are no tests', async () => {
    await createPage();
    fixture.componentInstance.tests.set([]);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.select-all-button')).toBeNull();
    expect(fixture.nativeElement.textContent).toContain('Ten zawodnik nie ma jeszcze wyników.');
  });

  it('rejects a reversed date range without requesting results', async () => {
    await createPage();
    const filters = fixture.nativeElement.querySelector('.history-filters') as HTMLElement;
    const dateActions = filters.querySelector('.date-actions') as HTMLElement;
    expect(dateActions.querySelector('.date-range')).not.toBeNull();
    expect(dateActions.querySelector('.load-button')).not.toBeNull();
    const dates = fixture.nativeElement.querySelectorAll('input[type="date"]') as NodeListOf<HTMLInputElement>;
    dates[0].value = '2025-01-01';
    dates[0].dispatchEvent(new Event('input'));
    dates[1].value = '2024-01-01';
    dates[1].dispatchEvent(new Event('input'));
    (fixture.nativeElement.querySelector('.load-button') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Data początkowa nie może być późniejsza');
    expect(filters.querySelector('.filter-error')).not.toBeNull();
    expect(dateActions.querySelector('.load-button')).not.toBeNull();
    expect(athletesApi.getResultHistory).not.toHaveBeenCalled();
  });

  it('shows a not-found message when the athlete does not exist', async () => {
    athletesApi = {
      getById: vi.fn(() => throwError(() => new HttpErrorResponse({ status: 404 }))),
      getTestsWithResults: vi.fn(() => of([])),
      getResultHistory: vi.fn(() => of([])),
      getAll: vi.fn(() => of([])),
      updateAthlete: vi.fn(),
    };
    testsApi = { getAll: vi.fn(() => of([])) };
    await TestBed.configureTestingModule({
      imports: [AthleteProfilePage],
      providers: [
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { paramMap: of(convertToParamMap({ id: '8' })) } },
        { provide: AthletesApiService, useValue: athletesApi },
        { provide: TestsApiService, useValue: testsApi },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(AthleteProfilePage);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Nie znaleziono zawodnika.');
  });

  it('preserves profile results when a refresh request fails', async () => {
    await createPage(athlete, () => of(results));
    const component = fixture.componentInstance;
    component.selectedTestIds.set([3, 5]);
    component.loadResults();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelectorAll('.matrix .result-value')).toHaveLength(3);

    athletesApi.getResultHistory.mockImplementationOnce(() =>
      throwError(() => new HttpErrorResponse({ status: 500 })),
    );
    component.loadResults();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Nie udało się pobrać historii wyników.');
    expect(fixture.nativeElement.querySelectorAll('.matrix .result-value')).toHaveLength(3);
  });

  it('opens result editing after a double click and preselects the athlete for new results', async () => {
    await createPage();
    const component = fixture.componentInstance;
    component.history.set(results);
    component.historyLoaded.set(true);
    fixture.detectChanges();
    const resultButton = fixture.nativeElement.querySelector('.result-value') as HTMLButtonElement;
    resultButton.dispatchEvent(new MouseEvent('dblclick', { bubbles: true }));
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(component.selectedResult()?.id).toBe(10);
    expect(fixture.debugElement.query(By.directive(ResultFormModalComponent)).componentInstance.initialAthleteId).toBeNull();
    component.closeResultForm();
    component.openAddResult();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(fixture.debugElement.query(By.directive(ResultFormModalComponent)).componentInstance.initialAthleteId).toBe(8);
  });

  it('spaces chart dates proportionally and separates or merges selected series scales', async () => {
    await createPage();
    const component = fixture.componentInstance;
    component.tests.set([
      tests[0],
      { ...tests[1], unit: 's' },
      { id: 7, name: 'Bieg 200 m', unit: 's', category: 'TIME', higherIsBetter: false, active: true },
    ]);
    component.selectedTestIds.set([3, 5, 7]);
    component.history.set([
      results[0],
      { ...results[0], id: 13, resultDate: '2024-05-11', value: 12.2 },
      { ...results[0], id: 14, resultDate: '2024-07-10', value: 11.8 },
      results[2],
      { ...results[0], id: 15, testId: 7, testName: 'Bieg 200 m', resultDate: '2024-05-01', value: 26.4 },
    ]);
    component.historyLoaded.set(true);
    fixture.detectChanges();

    const initialChart = component.chart();
    expect(initialChart?.scales).toHaveLength(1);
    const dateTicks = initialChart!.dates;
    const firstGap = dateTicks[1].x - dateTicks[0].x;
    const secondGap = dateTicks[2].x - dateTicks[1].x;
    expect(secondGap / firstGap).toBeCloseTo(6, 0);

    component.selectedSeriesIds.set([3, 5]);
    component.separateSelectedScales();
    expect(component.chart()?.scales).toHaveLength(2);
    expect(component.chart()?.scales.map((scale) => scale.seriesTestIds)).toContainEqual([3, 5]);
    expect(component.selectedSeriesIds()).toEqual([]);

    component.selectedSeriesIds.set([3]);
    component.mergeSelectedScales();
    expect(component.chart()?.scales).toHaveLength(2);
    expect(component.chart()?.scales.map((scale) => scale.seriesTestIds)).toContainEqual([3, 7]);
    expect(component.chart()?.scales.map((scale) => scale.seriesTestIds)).toContainEqual([5]);
    expect(component.selectedSeriesIds()).toEqual([]);

    component.selectedSeriesIds.set([5]);
    component.mergeSelectedScales();
    expect(component.chart()?.scales).toHaveLength(1);
    expect(component.chart()?.scales[0].seriesTestIds).toEqual([3, 5, 7]);
    expect(component.selectedSeriesIds()).toEqual([]);
  });

  it('renders only the athlete response fields defined by the API contract', async () => {
    await createPage();
    expect(fixture.componentInstance.athlete()).toEqual({
      id: 8,
      firstName: 'Jan',
      lastName: 'Kowalski',
      birthYear: 2010,
      gender: 'MALE',
      active: true,
    });
    expect(fixture.nativeElement.textContent).not.toContain('hibernateLazyInitializer');
  });
});

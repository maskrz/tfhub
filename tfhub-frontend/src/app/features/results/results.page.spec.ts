import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Observable, Subject, of, throwError } from 'rxjs';
import { AthleteResponse } from '../athletes/athlete.model';
import { AthletesApiService } from '../athletes/athletes-api.service';
import { TestResponse } from '../tests/test.model';
import { TestsApiService } from '../tests/tests-api.service';
import { CreateMultipleResultsResponse, ResultResponse } from './result.model';
import { ResultsApiService } from './results-api.service';
import { ResultsPage } from './results.page';

const athletes: AthleteResponse[] = [
  { id: 1, firstName: 'Jan', lastName: 'Kowalski', birthYear: 2010, gender: 'MALE', active: true },
  { id: 2, firstName: 'Anna', lastName: 'Nowak', birthYear: 2011, gender: 'FEMALE', active: false },
  { id: 3, firstName: 'Piotr', lastName: 'Zieliński', birthYear: 2009, gender: 'MALE', active: true },
];

const tests: TestResponse[] = [
  { id: 10, name: 'Bieg na 40 m', unit: 's', category: 'TIME', higherIsBetter: false, active: true },
  { id: 20, name: 'Skok w dal', unit: 'm', category: 'DISTANCE', higherIsBetter: true, active: false },
];

const results: ResultResponse[] = [
  { id: 3, athleteId: 1, testId: 10, resultDate: '2025-02-01', value: 6.125, type: 'TRAINING' },
  { id: 2, athleteId: 2, testId: 20, resultDate: '2025-02-01', value: 4.5, type: 'COMPETITION' },
  { id: 1, athleteId: 1, testId: 20, resultDate: '2025-01-01', value: 5.2, type: 'COMPETITION' },
];

describe('ResultsPage', () => {
  let fixture: ComponentFixture<ResultsPage>;
  let resultsApi: {
    getAll: ReturnType<typeof vi.fn>;
    createResult: ReturnType<typeof vi.fn>;
    createMultipleResults: ReturnType<typeof vi.fn>;
  };
  let athletesApi: { getAll: ReturnType<typeof vi.fn> };
  let testsApi: { getAll: ReturnType<typeof vi.fn> };

  async function createPage(
    resultLoader: () => Observable<ResultResponse[]> = () => of(results),
    athleteLoader: () => Observable<AthleteResponse[]> = () => of(athletes),
    testLoader: () => Observable<TestResponse[]> = () => of(tests),
    createLoader: (request: object) => Observable<ResultResponse> = () => of(results[0]),
    multipleCreateLoader: (request: object) => Observable<CreateMultipleResultsResponse> = () =>
      of({ createdCount: 1, results: [results[0]] }),
  ): Promise<void> {
    resultsApi = {
      getAll: vi.fn(resultLoader),
      createResult: vi.fn(createLoader),
      createMultipleResults: vi.fn(multipleCreateLoader),
    };
    athletesApi = { getAll: vi.fn(athleteLoader) };
    testsApi = { getAll: vi.fn(testLoader) };

    await TestBed.configureTestingModule({
      imports: [ResultsPage],
      providers: [
        { provide: ResultsApiService, useValue: resultsApi },
        { provide: AthletesApiService, useValue: athletesApi },
        { provide: TestsApiService, useValue: testsApi },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(ResultsPage);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  function setInput(selector: string, value: string): void {
    const input = fixture.nativeElement.querySelector(selector) as HTMLInputElement;
    input.value = value;
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  }

  function setSelect(selector: string, value: string): void {
    const select = fixture.nativeElement.querySelector(selector) as HTMLSelectElement;
    select.value = value;
    select.dispatchEvent(new Event('change'));
    fixture.detectChanges();
  }

  function rowTexts(): string[] {
    return Array.from(fixture.nativeElement.querySelectorAll('tbody tr'), (row) =>
      (row as HTMLElement).textContent?.replace(/\s+/g, ' ').trim() ?? '',
    );
  }

  function expectSingleLoad(): void {
    expect(resultsApi.getAll).toHaveBeenCalledTimes(1);
    expect(athletesApi.getAll).toHaveBeenCalledTimes(1);
    expect(testsApi.getAll).toHaveBeenCalledTimes(1);
  }

  function openCreateForm(): void {
    (fixture.nativeElement.querySelector('.page-heading .button--primary') as HTMLButtonElement).click();
    fixture.detectChanges();
  }

  function openMultipleCreateForm(): void {
    const button = Array.from(fixture.nativeElement.querySelectorAll('.page-actions button'))
      .find((candidate) => (candidate as HTMLButtonElement).textContent?.includes('Dodaj wiele wyników')) as HTMLButtonElement;
    button.click();
    fixture.detectChanges();
  }

  function setFormValue(selector: string, value: string): void {
    const control = fixture.nativeElement.querySelector(selector) as HTMLInputElement | HTMLSelectElement;
    control.value = value;
    control.dispatchEvent(new Event(control instanceof HTMLSelectElement ? 'change' : 'input'));
    fixture.detectChanges();
  }

  function submitCreateForm(): void {
    (fixture.nativeElement.querySelector('.save-button') as HTMLButtonElement).click();
    fixture.detectChanges();
  }

  function submitMultipleCreateForm(): void {
    (fixture.nativeElement.querySelector('#multiple-results-form') as HTMLFormElement)
      .dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
    fixture.detectChanges();
  }

  function setMultipleResult(athleteId: number, value: string): void {
    setFormValue(`#multiple-result-value-${athleteId}`, value);
  }

  function fillMultipleSharedFields(): void {
    setFormValue('#multiple-result-test', '10');
    setFormValue('#multiple-result-date', '2025-02-01');
    setFormValue('#multiple-result-type', 'TRAINING');
  }

  it('loads the results and lookup collections once and shows date-descending results by default', async () => {
    await createPage();

    expectSingleLoad();
    expect(rowTexts()[0]).toContain('01.02.2025');
    expect(rowTexts()[0]).toContain('Jan Kowalski');
    expect(rowTexts()[0]).toContain('6,125 s');
    expect(rowTexts()[0]).toContain('Trening');
    expect(rowTexts()[1]).toContain('Anna Nowak');
    expect(rowTexts()[1]).toContain('Skok w dal');
    expect(rowTexts()[2]).toContain('01.01.2025');
    expect(fixture.nativeElement.querySelector('thead th')?.getAttribute('aria-sort')).toBe('descending');
    expect(fixture.nativeElement.querySelector('thead th:last-child')?.textContent?.trim()).toBe('');
    expect(fixture.nativeElement.querySelector('.search-filter > span:first-child')?.textContent).toBe('Szukaj');
  });

  it('toggles date sorting locally without additional API requests', async () => {
    await createPage();
    (fixture.nativeElement.querySelector('.sort-button') as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(rowTexts()[0]).toContain('01.01.2025');
    expect(rowTexts()[1]).toContain('Anna Nowak');
    expect(rowTexts()[2]).toContain('Jan Kowalski');
    expect(fixture.nativeElement.querySelector('thead th')?.getAttribute('aria-sort')).toBe('ascending');
    expectSingleLoad();
  });

  it('searches both athlete and test names locally', async () => {
    await createPage();

    setInput('input[type="search"]', '  ANNA skok  ');

    expect(rowTexts()).toHaveLength(1);
    expect(rowTexts()[0]).toContain('Anna Nowak');
    expect(rowTexts()[0]).toContain('Skok w dal');
    expectSingleLoad();
  });

  it('filters by athlete and test locally', async () => {
    await createPage();

    setSelect('[aria-label="Filtruj według zawodnika"]', '2');
    expect(rowTexts()).toHaveLength(1);
    expect(rowTexts()[0]).toContain('Anna Nowak');

    setSelect('[aria-label="Filtruj według zawodnika"]', '');
    setSelect('[aria-label="Filtruj według testu"]', '10');
    expect(rowTexts()).toHaveLength(1);
    expect(rowTexts()[0]).toContain('Bieg na 40 m');
    expectSingleLoad();
  });

  it('filters locally by inclusive date boundaries and result type', async () => {
    await createPage();

    setInput('[aria-label="Data od"]', '2025-02-01');
    setInput('[aria-label="Data do"]', '2025-02-01');
    expect(rowTexts()).toHaveLength(2);

    setSelect('[aria-label="Filtruj według rodzaju"]', 'COMPETITION');
    expect(rowTexts()).toHaveLength(1);
    expect(rowTexts()[0]).toContain('Zawody');

    setSelect('[aria-label="Filtruj według rodzaju"]', 'TRAINING');
    expect(rowTexts()).toHaveLength(1);
    expect(rowTexts()[0]).toContain('Trening');
    expectSingleLoad();
  });

  it('combines all filters and can clear them without reloading data', async () => {
    await createPage();

    setInput('input[type="search"]', 'skok');
    setSelect('[aria-label="Filtruj według zawodnika"]', '2');
    setSelect('[aria-label="Filtruj według testu"]', '20');
    setInput('[aria-label="Data od"]', '2025-02-01');
    setSelect('[aria-label="Filtruj według rodzaju"]', 'COMPETITION');

    expect(rowTexts()).toHaveLength(1);
    expect(rowTexts()[0]).toContain('Anna Nowak');

    (fixture.nativeElement.querySelector('.clear-button') as HTMLButtonElement | null)?.click();
    fixture.detectChanges();
    expect(rowTexts()).toHaveLength(3);
    expectSingleLoad();
  });

  it('keeps historical results linked to inactive athletes and tests visible and filterable', async () => {
    await createPage();

    expect(rowTexts()[1]).toContain('Anna Nowak');
    expect(rowTexts()[1]).toContain('Skok w dal');
    expect(
      (fixture.nativeElement.querySelector('[aria-label="Filtruj według zawodnika"]') as HTMLSelectElement)
        .textContent,
    ).toContain('(nieaktywny)');
    expect(
      (fixture.nativeElement.querySelector('[aria-label="Filtruj według testu"]') as HTMLSelectElement)
        .textContent,
    ).toContain('(nieaktywny)');
  });

  it('shows the empty state when the API returns no results', async () => {
    await createPage(() => of([]));
    setInput('input[type="search"]', 'brak');
    expect(fixture.nativeElement.textContent).toContain('Brak wyników do wyświetlenia');
    expect(fixture.nativeElement.textContent).not.toContain('Nie znaleziono wyników pasujących');
  });

  it('shows the no-matching-results state and clears filters locally', async () => {
    await createPage();
    setInput('input[type="search"]', 'brak');
    expect(fixture.nativeElement.textContent).toContain('Nie znaleziono wyników pasujących do filtrów');
    expect(fixture.nativeElement.querySelector('.clear-button')).toBeTruthy();
    expectSingleLoad();
  });

  it('shows a loading state while the initial collections are pending', async () => {
    const resultsSubject = new Subject<ResultResponse[]>();
    const athletesSubject = new Subject<AthleteResponse[]>();
    const testsSubject = new Subject<TestResponse[]>();
    resultsApi = {
      getAll: vi.fn(() => resultsSubject.asObservable()),
      createResult: vi.fn(() => of(results[0])),
      createMultipleResults: vi.fn(() => of({ createdCount: 1, results: [results[0]] })),
    };
    athletesApi = { getAll: vi.fn(() => athletesSubject.asObservable()) };
    testsApi = { getAll: vi.fn(() => testsSubject.asObservable()) };

    await TestBed.configureTestingModule({
      imports: [ResultsPage],
      providers: [
        { provide: ResultsApiService, useValue: resultsApi },
        { provide: AthletesApiService, useValue: athletesApi },
        { provide: TestsApiService, useValue: testsApi },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(ResultsPage);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Ładowanie wyników');

    resultsSubject.next(results);
    resultsSubject.complete();
    athletesSubject.next(athletes);
    athletesSubject.complete();
    testsSubject.next(tests);
    testsSubject.complete();
    fixture.detectChanges();
    expect(rowTexts()).toHaveLength(3);
  });

  it('handles unauthorized API errors and retries loading', async () => {
    const failingLoader = vi.fn()
      .mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 401 })))
      .mockReturnValueOnce(of(results));
    await createPage(failingLoader, () => of(athletes), () => of(tests));
    expect(fixture.nativeElement.textContent).toContain('Sesja wygasła');
    (fixture.nativeElement.querySelector('.retry-button') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(failingLoader).toHaveBeenCalledTimes(2);
    expect(rowTexts()).toHaveLength(3);
  });

  it('keeps Edit and Delete actions visibly unavailable', async () => {
    await createPage();

    const buttons = Array.from(fixture.nativeElement.querySelectorAll('.action-cell button')) as HTMLButtonElement[];
    expect(buttons.map((button) => button.textContent?.trim())).toEqual(['Edytuj', 'Usuń', 'Edytuj', 'Usuń', 'Edytuj', 'Usuń']);
    expect(buttons.every((button) => button.disabled)).toBe(true);
    expect(buttons[0].title).toContain('osobnej funkcji');
  });

  it('opens and cancels the add-result modal with the correct fields and local date default', async () => {
    await createPage();
    openCreateForm();

    expect(fixture.nativeElement.querySelector('#modal-title')?.textContent).toContain('Dodaj wynik');
    expect(fixture.nativeElement.querySelector('#result-athlete')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('#result-test')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('#result-date')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('#result-value')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('#result-type')).toBeTruthy();

    const now = new Date();
    const expectedDate = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`;
    expect((fixture.nativeElement.querySelector('#result-date') as HTMLInputElement).value).toBe(expectedDate);

    (fixture.nativeElement.querySelector('.cancel-button') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('app-result-form-modal')).toBeNull();
    expect(resultsApi.createResult).not.toHaveBeenCalled();
    expect(rowTexts()).toHaveLength(3);
    expectSingleLoad();
  });

  it('offers only active athletes and tests, result types, and selected test unit', async () => {
    await createPage();
    openCreateForm();

    const athleteOptions = Array.from(
      fixture.nativeElement.querySelectorAll('#result-athlete option'),
      (option) => (option as HTMLOptionElement).textContent?.trim(),
    );
    const testOptions = Array.from(
      fixture.nativeElement.querySelectorAll('#result-test option'),
      (option) => (option as HTMLOptionElement).textContent?.trim(),
    );
    expect(athleteOptions).not.toContain('Anna Nowak (nieaktywny)');
    expect(athleteOptions).not.toContain('Anna Nowak');
    expect(athleteOptions).toEqual(['Wybierz zawodnika', 'Jan Kowalski', 'Piotr Zieliński']);
    expect(testOptions).toEqual(['Wybierz test', 'Bieg na 40 m']);

    setFormValue('#result-test', '10');
    expect(fixture.nativeElement.querySelector('#result-value-unit')?.textContent?.trim()).toBe('s');
    expect(fixture.nativeElement.querySelector('#result-type option[value="TRAINING"]')?.textContent).toContain('Trening');
    expect(fixture.nativeElement.querySelector('#result-type option[value="COMPETITION"]')?.textContent).toContain('Zawody');
    expectSingleLoad();
  });

  it('validates required fields, positive values, decimal precision, and database range', async () => {
    const createResult = vi.fn(() => of(results[0]));
    await createPage(undefined, undefined, undefined, createResult);
    openCreateForm();
    submitCreateForm();
    expect(resultsApi.createResult).not.toHaveBeenCalled();
    expect(fixture.nativeElement.querySelectorAll('.field-error').length).toBe(4);

    setFormValue('#result-athlete', '1');
    setFormValue('#result-test', '10');
    setFormValue('#result-date', '2025-02-01');
    setFormValue('#result-type', 'TRAINING');

    for (const invalidValue of ['0', '-1', '1.2345', '1000000000', '1e3']) {
      setFormValue('#result-value', invalidValue);
      submitCreateForm();
      expect(resultsApi.createResult).not.toHaveBeenCalled();
      expect(fixture.nativeElement.querySelector('#result-value-error')).toBeTruthy();
    }

    setFormValue('#result-value', '1,234');
    submitCreateForm();
    expect(resultsApi.createResult).toHaveBeenCalledWith({
      athleteId: 1,
      testId: 10,
      resultDate: '2025-02-01',
      value: 1.234,
      type: 'TRAINING',
    });
  });

  it('adds a created result locally while preserving active filters and sort direction', async () => {
    const createdResult: ResultResponse = {
      id: 4,
      athleteId: 1,
      testId: 10,
      resultDate: '2025-03-01',
      value: 6.01,
      type: 'TRAINING',
    };
    const createResult = vi.fn(() => of(createdResult));
    await createPage(undefined, undefined, undefined, createResult);
    setSelect('[aria-label="Filtruj według zawodnika"]', '1');
    setSelect('[aria-label="Filtruj według rodzaju"]', 'TRAINING');
    (fixture.nativeElement.querySelector('.sort-button') as HTMLButtonElement).click();
    fixture.detectChanges();
    openCreateForm();

    setFormValue('#result-athlete', '1');
    setFormValue('#result-test', '10');
    setFormValue('#result-date', createdResult.resultDate);
    setFormValue('#result-value', '6.01');
    setFormValue('#result-type', 'TRAINING');
    submitCreateForm();
    fixture.detectChanges();

    expect(createResult).toHaveBeenCalledWith({
      athleteId: 1,
      testId: 10,
      resultDate: '2025-03-01',
      value: 6.01,
      type: 'TRAINING',
    });
    expect(fixture.nativeElement.querySelector('app-result-form-modal')).toBeNull();
    expect(fixture.nativeElement.textContent).toContain('Wynik został dodany.');
    expect(rowTexts()).toHaveLength(2);
    expect(rowTexts()[0]).toContain('01.02.2025');
    expect(rowTexts()[1]).toContain('01.03.2025');
    expectSingleLoad();
  });

  it('keeps the form open with entered values after an API error', async () => {
    const createResult = vi.fn(() =>
      throwError(() => new HttpErrorResponse({ status: 409, error: { title: 'Test is inactive' } })),
    );
    await createPage(undefined, undefined, undefined, createResult);
    openCreateForm();
    setFormValue('#result-athlete', '1');
    setFormValue('#result-test', '10');
    setFormValue('#result-date', '2025-02-01');
    setFormValue('#result-value', '4.125');
    setFormValue('#result-type', 'COMPETITION');
    submitCreateForm();

    expect(fixture.nativeElement.querySelector('app-result-form-modal')).toBeTruthy();
    expect(fixture.nativeElement.textContent).toContain('Wybrany test jest nieaktywny');
    expect((fixture.nativeElement.querySelector('#result-athlete') as HTMLSelectElement).value).toBe('1');
    expect((fixture.nativeElement.querySelector('#result-value') as HTMLInputElement).value).toBe('4.125');
    expect((fixture.nativeElement.querySelector('#result-type') as HTMLSelectElement).value).toBe('COMPETITION');
    expect(rowTexts()).toHaveLength(3);
    expectSingleLoad();
  });

  it('explains backend rejection of an inactive athlete and preserves the form', async () => {
    const createResult = vi.fn(() =>
      throwError(() => new HttpErrorResponse({ status: 409, error: { title: 'Athlete is inactive' } })),
    );
    await createPage(undefined, undefined, undefined, createResult);
    openCreateForm();
    setFormValue('#result-athlete', '1');
    setFormValue('#result-test', '10');
    setFormValue('#result-date', '2025-02-01');
    setFormValue('#result-value', '4.125');
    setFormValue('#result-type', 'COMPETITION');
    submitCreateForm();

    expect(fixture.nativeElement.textContent).toContain('Nie można dodać wyniku zawodnikowi nieaktywnemu');
    expect((fixture.nativeElement.querySelector('#result-athlete') as HTMLSelectElement).value).toBe('1');
    expect(fixture.nativeElement.querySelector('app-result-form-modal')).toBeTruthy();
  });

  it('prevents duplicate submissions while the create request is pending', async () => {
    const pending = new Subject<ResultResponse>();
    const createResult = vi.fn(() => pending.asObservable());
    await createPage(undefined, undefined, undefined, createResult);
    openCreateForm();
    setFormValue('#result-athlete', '1');
    setFormValue('#result-test', '10');
    setFormValue('#result-date', '2025-02-01');
    setFormValue('#result-value', '7.5');
    setFormValue('#result-type', 'TRAINING');
    submitCreateForm();
    (fixture.nativeElement.querySelector('#result-create-form') as HTMLFormElement)
      .dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));

    expect(createResult).toHaveBeenCalledTimes(1);
    expect((fixture.nativeElement.querySelector('.save-button') as HTMLButtonElement).disabled).toBe(true);
    expect((fixture.nativeElement.querySelector('.cancel-button') as HTMLButtonElement).disabled).toBe(true);
    pending.next(results[0]);
    pending.complete();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('app-result-form-modal')).toBeNull();
    expectSingleLoad();
  });

  it('opens the batch modal with eligible athletes, empty inputs, today, and current test unit', async () => {
    await createPage();
    openMultipleCreateForm();

    expect(fixture.nativeElement.querySelector('#multiple-modal-title')?.textContent).toContain('Dodaj wiele wyników');
    expect(fixture.nativeElement.querySelector('#multiple-result-test')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('#multiple-result-date')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('#multiple-result-type')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('.selected-unit')?.textContent).toContain('Wybierz test');
    setFormValue('#multiple-result-test', '10');
    expect(fixture.nativeElement.querySelector('.selected-unit')?.textContent).toContain('s');
    const athleteListText = (fixture.nativeElement.querySelector('.athlete-list') as HTMLElement).textContent;
    expect(athleteListText).toContain('Jan Kowalski');
    expect(athleteListText).toContain('Piotr Zieliński');
    expect(athleteListText).not.toContain('Anna Nowak');
    expect(fixture.nativeElement.querySelector('#multiple-result-value-1')).toBeTruthy();
    expect((fixture.nativeElement.querySelector('#multiple-result-value-1') as HTMLInputElement).value).toBe('');
    expect(fixture.nativeElement.querySelector('#multiple-results-form')?.textContent).not.toContain('Skok w dal');

    const now = new Date();
    const expectedDate = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`;
    expect((fixture.nativeElement.querySelector('#multiple-result-date') as HTMLInputElement).value).toBe(expectedDate);
    expectSingleLoad();

    (fixture.nativeElement.querySelector('.cancel-button') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('app-multiple-results-form-modal')).toBeNull();
    expect(resultsApi.createMultipleResults).not.toHaveBeenCalled();
  });

  it('updates the displayed unit and clears results when the selected test changes', async () => {
    const activeTests: TestResponse[] = [
      tests[0],
      { ...tests[1], active: true },
    ];
    await createPage(undefined, undefined, () => of(activeTests));
    openMultipleCreateForm();
    setFormValue('#multiple-result-test', '10');
    setMultipleResult(1, '7.25');
    expect(fixture.nativeElement.querySelector('.selected-unit')?.textContent).toContain('s');

    setFormValue('#multiple-result-test', '20');

    expect(fixture.nativeElement.querySelector('.selected-unit')?.textContent).toContain('m');
    expect((fixture.nativeElement.querySelector('#multiple-result-value-1') as HTMLInputElement).value).toBe('');
    expect(fixture.nativeElement.textContent).toContain('Po zmianie testu wpisane wyniki zostały wyczyszczone.');
  });

  it('requires shared fields and at least one result, and validates positive precision without submitting', async () => {
    await createPage();
    openMultipleCreateForm();
    setFormValue('#multiple-result-date', '');
    submitMultipleCreateForm();

    expect(resultsApi.createMultipleResults).not.toHaveBeenCalled();
    expect(fixture.nativeElement.querySelector('#multiple-result-test-error')?.textContent).toContain('Wybierz test');
    expect(fixture.nativeElement.querySelector('#multiple-result-date-error')?.textContent).toContain('Wybierz datę');
    expect(fixture.nativeElement.querySelector('#multiple-result-type-error')?.textContent).toContain('Wybierz rodzaj');
    expect(fixture.nativeElement.textContent).toContain('Wpisz co najmniej jeden wynik.');

    fillMultipleSharedFields();
    for (const invalidValue of ['0', '-1', '1.2345', '1000000000', '1e3']) {
      setMultipleResult(1, invalidValue);
      submitMultipleCreateForm();
      expect(resultsApi.createMultipleResults).not.toHaveBeenCalled();
      expect(fixture.nativeElement.querySelector(`#multiple-result-error-1`)?.textContent).toBeTruthy();
    }

    setMultipleResult(1, '1,234');
    submitMultipleCreateForm();
    expect(resultsApi.createMultipleResults).toHaveBeenCalledTimes(1);
    expect(resultsApi.createMultipleResults).toHaveBeenCalledWith({
      testId: 10,
      resultDate: '2025-02-01',
      type: 'TRAINING',
      results: [{ athleteId: 1, value: 1.234 }],
    });
  });

  it('rejects more than 100 non-empty results before making a batch request', async () => {
    const manyAthletes = Array.from({ length: 101 }, (_, index): AthleteResponse => ({
      id: index + 1,
      firstName: `Zawodnik${index + 1}`,
      lastName: 'Testowy',
      birthYear: 2010,
      gender: 'MALE',
      active: true,
    }));
    await createPage(() => of([]), () => of(manyAthletes));
    openMultipleCreateForm();
    fillMultipleSharedFields();
    for (const athlete of manyAthletes) {
      setMultipleResult(athlete.id, '1');
    }
    submitMultipleCreateForm();

    expect(resultsApi.createMultipleResults).not.toHaveBeenCalled();
    expect(fixture.nativeElement.textContent).toContain('maksymalnie 100 wyników');
  });

  it('submits one batch with only entered athletes and updates locally while preserving filters and sorting', async () => {
    const batchResponse: CreateMultipleResultsResponse = {
      createdCount: 2,
      results: [
        { id: 4, athleteId: 1, testId: 10, resultDate: '2025-03-01', value: 6.01, type: 'TRAINING' },
        { id: 5, athleteId: 3, testId: 10, resultDate: '2025-03-01', value: 7.2, type: 'TRAINING' },
      ],
    };
    const batchLoader = vi.fn(() => of(batchResponse));
    await createPage(undefined, undefined, undefined, undefined, batchLoader);
    setSelect('[aria-label="Filtruj według zawodnika"]', '1');
    setSelect('[aria-label="Filtruj według rodzaju"]', 'TRAINING');
    (fixture.nativeElement.querySelector('.sort-button') as HTMLButtonElement).click();
    fixture.detectChanges();
    openMultipleCreateForm();
    fillMultipleSharedFields();
    setMultipleResult(1, '6.01');
    setMultipleResult(3, '7.2');
    submitMultipleCreateForm();

    expect(batchLoader).toHaveBeenCalledTimes(1);
    expect(batchLoader).toHaveBeenCalledWith({
      testId: 10,
      resultDate: '2025-02-01',
      type: 'TRAINING',
      results: [
        { athleteId: 1, value: 6.01 },
        { athleteId: 3, value: 7.2 },
      ],
    });
    expect(fixture.nativeElement.querySelector('app-multiple-results-form-modal')).toBeNull();
    expect(fixture.nativeElement.textContent).toContain('Dodano 2 wyniki.');
    expect(rowTexts()).toHaveLength(2);
    expect(rowTexts()[0]).toContain('01.02.2025');
    expect(rowTexts()[1]).toContain('01.03.2025');
    expectSingleLoad();
  });

  it('keeps entered values and avoids partial local updates after a batch API failure', async () => {
    const batchLoader = vi.fn(() =>
      throwError(() => new HttpErrorResponse({ status: 409, error: { title: 'Athlete is inactive' } })),
    );
    await createPage(undefined, undefined, undefined, undefined, batchLoader);
    openMultipleCreateForm();
    fillMultipleSharedFields();
    setMultipleResult(1, '4.125');
    submitMultipleCreateForm();

    expect(fixture.nativeElement.querySelector('app-multiple-results-form-modal')).toBeTruthy();
    expect(fixture.nativeElement.textContent).toContain('Jeden z zawodników jest nieaktywny');
    expect((fixture.nativeElement.querySelector('#multiple-result-value-1') as HTMLInputElement).value).toBe('4.125');
    expect(rowTexts()).toHaveLength(3);
    expectSingleLoad();
  });

  it('prevents duplicate batch submissions while the request is pending', async () => {
    const pending = new Subject<CreateMultipleResultsResponse>();
    const batchLoader = vi.fn(() => pending.asObservable());
    await createPage(undefined, undefined, undefined, undefined, batchLoader);
    openMultipleCreateForm();
    fillMultipleSharedFields();
    setMultipleResult(1, '7.5');
    submitMultipleCreateForm();
    submitMultipleCreateForm();

    expect(batchLoader).toHaveBeenCalledTimes(1);
    expect((fixture.nativeElement.querySelector('.save-button') as HTMLButtonElement).disabled).toBe(true);
    expect((fixture.nativeElement.querySelector('.cancel-button') as HTMLButtonElement).disabled).toBe(true);
    pending.next({ createdCount: 1, results: [results[0]] });
    pending.complete();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('app-multiple-results-form-modal')).toBeNull();
    expectSingleLoad();
  });
});

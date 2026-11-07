import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Observable, Subject, of, throwError } from 'rxjs';
import { AthleteResponse } from '../athletes/athlete.model';
import { AthletesApiService } from '../athletes/athletes-api.service';
import { TestResponse } from '../tests/test.model';
import { TestsApiService } from '../tests/tests-api.service';
import { ResultResponse } from './result.model';
import { ResultsApiService } from './results-api.service';
import { ResultsPage } from './results.page';

const athletes: AthleteResponse[] = [
  { id: 1, firstName: 'Jan', lastName: 'Kowalski', birthYear: 2010, gender: 'MALE', active: true },
  { id: 2, firstName: 'Anna', lastName: 'Nowak', birthYear: 2011, gender: 'FEMALE', active: false },
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
  let resultsApi: { getAll: ReturnType<typeof vi.fn> };
  let athletesApi: { getAll: ReturnType<typeof vi.fn> };
  let testsApi: { getAll: ReturnType<typeof vi.fn> };

  async function createPage(
    resultLoader: () => Observable<ResultResponse[]> = () => of(results),
    athleteLoader: () => Observable<AthleteResponse[]> = () => of(athletes),
    testLoader: () => Observable<TestResponse[]> = () => of(tests),
  ): Promise<void> {
    resultsApi = { getAll: vi.fn(resultLoader) };
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
    resultsApi = { getAll: vi.fn(() => resultsSubject.asObservable()) };
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
});

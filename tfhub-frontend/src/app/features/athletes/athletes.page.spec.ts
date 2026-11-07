import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Observable, Subject, of, throwError } from 'rxjs';
import { AthleteResponse } from './athlete.model';
import { AthletesApiService } from './athletes-api.service';
import { AthletesPage } from './athletes.page';

const activeAthlete: AthleteResponse = {
  id: 1,
  firstName: 'Jan',
  lastName: 'Kowalski',
  birthYear: 2010,
  gender: 'MALE',
  active: true,
};

const inactiveAthlete: AthleteResponse = {
  id: 2,
  firstName: 'Anna',
  lastName: 'Nowak',
  birthYear: 2011,
  gender: 'FEMALE',
  active: false,
};

describe('AthletesPage', () => {
  let fixture: ComponentFixture<AthletesPage>;
  let api: {
    getAll: ReturnType<typeof vi.fn>;
    changeStatus: ReturnType<typeof vi.fn>;
  };

  async function createPage(
    getAll: () => Observable<AthleteResponse[]> = () => of([activeAthlete, inactiveAthlete]),
    overrides: Partial<typeof api> = {},
  ): Promise<void> {
    api = {
      getAll: vi.fn(getAll),
      changeStatus: vi.fn((_id: number, request: { active: boolean }) =>
        of(request.active ? activeAthlete : inactiveAthlete),
      ),
      ...overrides,
    };
    await TestBed.configureTestingModule({
      imports: [AthletesPage],
      providers: [{ provide: AthletesApiService, useValue: api }],
    }).compileComponents();
    fixture = TestBed.createComponent(AthletesPage);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  function setSearch(value: string): void {
    const input = fixture.nativeElement.querySelector('input[type="search"]') as HTMLInputElement;
    input.value = value;
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  }

  function showInactive(): void {
    const input = fixture.nativeElement.querySelector('.inactive-toggle input') as HTMLInputElement;
    input.checked = true;
    input.dispatchEvent(new Event('change'));
    fixture.detectChanges();
  }

  it('loads all athletes once and displays active athletes by default', async () => {
    await createPage();

    expect(api.getAll).toHaveBeenCalledTimes(1);
    expect(fixture.nativeElement.textContent).toContain('Jan Kowalski');
    expect(fixture.nativeElement.textContent).not.toContain('Anna Nowak');
    expect(fixture.nativeElement.querySelectorAll('tbody tr')).toHaveLength(1);
  });

  it('includes inactive athletes when the toggle is enabled without another request', async () => {
    await createPage();
    showInactive();

    expect(fixture.nativeElement.textContent).toContain('Anna Nowak');
    expect(fixture.nativeElement.querySelectorAll('tbody tr')).toHaveLength(2);
    expect(api.getAll).toHaveBeenCalledTimes(1);
  });

  it('searches first and last names case-insensitively and ignores query whitespace', async () => {
    await createPage();

    setSearch('  jAn  ');
    expect(fixture.nativeElement.textContent).toContain('Jan Kowalski');
    expect(fixture.nativeElement.textContent).not.toContain('Anna Nowak');

    setSearch(' KOW ');
    expect(fixture.nativeElement.textContent).toContain('Jan Kowalski');
    expect(api.getAll).toHaveBeenCalledTimes(1);
  });

  it('combines name search with active-status filtering locally', async () => {
    await createPage();
    showInactive();
    setSearch('ANNA NOW');
    expect(fixture.nativeElement.textContent).toContain('Anna Nowak');

    const toggle = fixture.nativeElement.querySelector('.inactive-toggle input') as HTMLInputElement;
    toggle.checked = false;
    toggle.dispatchEvent(new Event('change'));
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Nie znaleziono zawodników');
    expect(api.getAll).toHaveBeenCalledTimes(1);
  });

  it('changes status and updates the local row from the API response', async () => {
    const changeStatus = vi.fn((_id: number, request: { active: boolean }) =>
      of({ ...activeAthlete, active: request.active }),
    );
    await createPage(undefined, { changeStatus });

    const statusToggle = fixture.nativeElement.querySelector('.status-toggle') as HTMLButtonElement;
    expect(statusToggle.getAttribute('aria-checked')).toBe('true');
    statusToggle.click();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(changeStatus).toHaveBeenCalledWith(activeAthlete.id, { active: false });
    expect(fixture.nativeElement.textContent).not.toContain('Jan Kowalski');
    expect(api.getAll).toHaveBeenCalledTimes(1);
  });

  it('activates an inactive athlete and updates the local status', async () => {
    const changeStatus = vi.fn((_id: number, request: { active: boolean }) =>
      of({ ...inactiveAthlete, active: request.active }),
    );
    await createPage(undefined, { changeStatus });
    showInactive();

    const statusToggles = Array.from(
      fixture.nativeElement.querySelectorAll('.status-toggle'),
    ) as HTMLButtonElement[];
    const inactiveToggle = statusToggles.find(
      (toggle) => toggle.getAttribute('aria-checked') === 'false',
    );
    expect(inactiveToggle).toBeTruthy();
    inactiveToggle?.click();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(changeStatus).toHaveBeenCalledWith(inactiveAthlete.id, { active: true });
    const updatedToggles = Array.from(
      fixture.nativeElement.querySelectorAll('.status-toggle'),
    ) as HTMLButtonElement[];
    const updatedToggle = updatedToggles.find((toggle) =>
      toggle.getAttribute('aria-label')?.includes(inactiveAthlete.firstName),
    );
    expect(updatedToggle?.getAttribute('aria-checked')).toBe('true');
    expect(api.getAll).toHaveBeenCalledTimes(1);
  });

  it('keeps the previous status and displays an error when status update fails', async () => {
    const changeStatus = vi.fn(() =>
      throwError(() => new HttpErrorResponse({ status: 500 })),
    );
    await createPage(undefined, { changeStatus });

    (fixture.nativeElement.querySelector('.status-toggle') as HTMLButtonElement).click();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.status-toggle')?.getAttribute('aria-checked')).toBe(
      'true',
    );
    expect(fixture.nativeElement.textContent).toContain('Jan Kowalski');
    expect(fixture.nativeElement.querySelector('.status-error')?.textContent).toContain(
      'Nie udało się zmienić statusu zawodnika.',
    );
    expect(api.getAll).toHaveBeenCalledTimes(1);
  });

  it('prevents duplicate status requests while an update is in progress', async () => {
    const pending = new Subject<AthleteResponse>();
    const changeStatus = vi.fn(() => pending);
    await createPage(undefined, { changeStatus });

    const statusToggle = fixture.nativeElement.querySelector('.status-toggle') as HTMLButtonElement;
    statusToggle.click();
    fixture.detectChanges();

    expect(changeStatus).toHaveBeenCalledTimes(1);
    expect(statusToggle.disabled).toBe(true);
    statusToggle.click();
    expect(changeStatus).toHaveBeenCalledTimes(1);
  });

  it('shows loading and empty list states', async () => {
    const pending = new Subject<AthleteResponse[]>();
    await createPage(() => pending);
    expect(fixture.nativeElement.querySelector('[role="status"]')?.textContent).toContain(
      'Ładowanie zawodników',
    );

    pending.next([]);
    pending.complete();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Brak zawodników');
  });

  it('shows no-results state when the search does not match', async () => {
    await createPage();
    setSearch('nie ma takiej osoby');
    expect(fixture.nativeElement.textContent).toContain('Nie znaleziono zawodników');
  });

  it('shows an API error and retries the initial request', async () => {
    let attempts = 0;
    const getAll = () =>
      ++attempts === 1
        ? throwError(() => new HttpErrorResponse({ status: 500 }))
        : of([activeAthlete]);
    await createPage(getAll);

    expect(fixture.nativeElement.querySelector('.feedback.error')?.textContent).toContain(
      'Nie udało się pobrać listy zawodników.',
    );
    (fixture.nativeElement.querySelector('.retry-button') as HTMLButtonElement).click();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(api.getAll).toHaveBeenCalledTimes(2);
    expect(fixture.nativeElement.textContent).toContain('Jan Kowalski');
  });

  it('renders all five required columns and leaves add/edit as unavailable entry points', async () => {
    await createPage();

    const headings = Array.from(
      fixture.nativeElement.querySelectorAll('thead th'),
    ).map((heading) => (heading as HTMLTableCellElement).textContent?.trim());
    expect(headings).toEqual(['Zawodnik', 'Rok urodzenia', 'Płeć', 'Status', 'Akcje']);
    expect(fixture.nativeElement.querySelectorAll('tbody tr:first-child td')).toHaveLength(5);
    expect(fixture.nativeElement.querySelector('.button--primary')?.textContent).toContain(
      'Dodaj zawodnika',
    );
    expect((fixture.nativeElement.querySelector('.button--primary') as HTMLButtonElement).disabled).toBe(
      true,
    );
    expect((fixture.nativeElement.querySelector('.edit-button') as HTMLButtonElement).disabled).toBe(
      true,
    );
    expect(fixture.nativeElement.textContent).toContain('Mężczyzna');
  });
});

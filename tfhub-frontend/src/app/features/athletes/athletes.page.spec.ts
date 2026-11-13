import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Observable, Subject, of, throwError } from 'rxjs';
import { signal } from '@angular/core';
import { AthleteResponse, CreateAthleteRequest, UpdateAthleteRequest } from './athlete.model';
import { AthletesApiService } from './athletes-api.service';
import { AthletesPage } from './athletes.page';
import { TrainingGroupsApiService } from '../training-groups/training-groups-api.service';
import { AuthService } from '../../core/auth/auth.service';

const activeAthlete: AthleteResponse = {
  id: 1,
  firstName: 'Jan',
  lastName: 'Kowalski',
  birthYear: 2010,
  gender: 'MALE',
  active: true,
  trainingGroupId: 1,
};

const inactiveAthlete: AthleteResponse = {
  id: 2,
  firstName: 'Anna',
  lastName: 'Nowak',
  birthYear: 2011,
  gender: 'FEMALE',
  active: false,
  trainingGroupId: 2,
};

const createdAthlete: AthleteResponse = {
  id: 3,
  firstName: 'Ala',
  lastName: 'Zielińska',
  birthYear: 2008,
  gender: 'FEMALE',
  aliases: [],
  active: true,
};

const updatedAthlete: AthleteResponse = {
  ...activeAthlete,
  firstName: 'Janusz',
  lastName: 'Nowak',
  birthYear: 2012,
  gender: 'MALE',
};

describe('AthletesPage', () => {
  let fixture: ComponentFixture<AthletesPage>;
  let api: {
    getAll: ReturnType<typeof vi.fn>;
    createAthlete: ReturnType<typeof vi.fn>;
    updateAthlete: ReturnType<typeof vi.fn>;
    changeStatus: ReturnType<typeof vi.fn>;
  };

  async function createPage(
    getAll: () => Observable<AthleteResponse[]> = () => of([activeAthlete, inactiveAthlete]),
    overrides: Partial<typeof api> = {},
  ): Promise<void> {
    api = {
      getAll: vi.fn(getAll),
      createAthlete: vi.fn((_request: CreateAthleteRequest) => of(createdAthlete)),
      updateAthlete: vi.fn((_id: number, _request: UpdateAthleteRequest) => of(updatedAthlete)),
      changeStatus: vi.fn((_id: number, request: { active: boolean }) =>
        of(request.active ? activeAthlete : inactiveAthlete),
      ),
      ...overrides,
    };
    await TestBed.configureTestingModule({
      imports: [AthletesPage],
      providers: [
        provideRouter([]),
        { provide: AthletesApiService, useValue: api },
        { provide: TrainingGroupsApiService, useValue: { getAccessible: () => of([]) } },
        {
          provide: AuthService,
          useValue: {
            currentUser: signal({ authenticated: true, userId: 1, username: 'admin', role: 'ADMIN', athleteId: null }),
          },
        },
      ],
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

  function setGender(value: string): void {
    const select = fixture.nativeElement.querySelector('.gender-filter select') as HTMLSelectElement;
    select.value = value;
    select.dispatchEvent(new Event('change'));
    fixture.detectChanges();
  }

  function setBirthYear(bound: 'from' | 'to', value: string): void {
    const input = fixture.nativeElement.querySelector(
      bound === 'from' ? '[aria-label="Od roku urodzenia"]' : '[aria-label="Do roku urodzenia"]',
    ) as HTMLInputElement;
    input.value = value;
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  }

  function rowNames(): string[] {
    const rows = fixture.nativeElement.querySelectorAll('tbody tr') as NodeListOf<HTMLTableRowElement>;
    return Array.from(rows, (row) => row.querySelector('td strong')?.textContent?.trim() ?? '');
  }

  function openCreate(): void {
    (fixture.nativeElement.querySelector('.page-heading .button--primary') as HTMLButtonElement).click();
    fixture.detectChanges();
  }

  function openEdit(athleteName = 'Jan Kowalski'): void {
    const row = Array.from(fixture.nativeElement.querySelectorAll('tbody tr')).find((candidate) =>
      (candidate as HTMLElement).textContent?.includes(athleteName),
    ) as HTMLElement | undefined;
    (row?.querySelector('.edit-button') as HTMLButtonElement).click();
    fixture.detectChanges();
  }

  function setTextInput(selector: string, value: string): void {
    const input = fixture.nativeElement.querySelector(selector) as HTMLInputElement;
    input.value = value;
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  }

  function setFormSelect(selector: string, value: string): void {
    const select = fixture.nativeElement.querySelector(selector) as HTMLSelectElement;
    select.value = value;
    select.dispatchEvent(new Event('change'));
    fixture.detectChanges();
  }

  function submitAthleteForm(): void {
    (fixture.nativeElement.querySelector('.save-button') as HTMLButtonElement).click();
    fixture.detectChanges();
  }

  it('loads all athletes once and displays active athletes by default', async () => {
    await createPage();

    expect(api.getAll).toHaveBeenCalledTimes(1);
    expect(fixture.nativeElement.querySelector('.search-filter > span:first-child')?.textContent).toBe('Szukaj zawodnika');
    expect(fixture.nativeElement.querySelector('.gender-filter > span')?.textContent).toBe('Płeć');
    expect(fixture.nativeElement.querySelector('.birth-year-filter .filter-field:first-child > span')?.textContent).toBe('Od roku');
    expect(fixture.nativeElement.querySelector('.birth-year-filter .filter-field:last-child > span')?.textContent).toBe('Do roku');
    expect(fixture.nativeElement.querySelector('.inactive-filter > span')?.textContent).toBe('Pokaż nieaktywnych');
    expect(
      (fixture.nativeElement.querySelector('.inactive-toggle input') as HTMLInputElement).getAttribute('aria-labelledby'),
    ).toBe('inactive-filter-label');
    expect(fixture.nativeElement.textContent).toContain('Jan Kowalski');
    expect(fixture.nativeElement.textContent).not.toContain('Anna Nowak');
    expect(fixture.nativeElement.querySelectorAll('tbody tr')).toHaveLength(1);
  });

  it('displays group names and filters athletes by one or more groups locally', async () => {
    await createPage();
    fixture.componentInstance.groups.set([
      { id: 1, name: 'Grupa 1' },
      { id: 2, name: 'Grupa 2' },
    ]);
    fixture.componentInstance.allAthletes.set([
      activeAthlete,
      inactiveAthlete,
      { ...createdAthlete, trainingGroupId: null },
    ]);
    showInactive();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Grupa 1');
    expect(fixture.nativeElement.textContent).toContain('Grupa 2');
    expect(fixture.nativeElement.textContent).toContain('Bez grupy');

    fixture.componentInstance.onGroupIdsChange([2, 0]);
    fixture.detectChanges();
    expect(rowNames()).toEqual(['Anna Nowak', 'Ala Zielińska']);

    fixture.componentInstance.onGroupIdsChange([]);
    fixture.detectChanges();
    expect(rowNames()).toHaveLength(3);
    expect(api.getAll).toHaveBeenCalledTimes(1);
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

  it('filters by gender and birth-year range together with name and active status locally', async () => {
    await createPage();
    showInactive();
    setGender('FEMALE');
    setBirthYear('from', '2011');
    setBirthYear('to', '2011');
    setSearch('anna');

    expect(rowNames()).toEqual(['Anna Nowak']);
    expect(api.getAll).toHaveBeenCalledTimes(1);

    setGender('MALE');
    expect(fixture.nativeElement.textContent).toContain('Nie znaleziono zawodników');
    expect(api.getAll).toHaveBeenCalledTimes(1);
  });

  it('supports searching by the Polish gender label', async () => {
    await createPage();
    showInactive();
    setSearch('kobieta');

    expect(rowNames()).toEqual(['Anna Nowak']);
    expect(api.getAll).toHaveBeenCalledTimes(1);
  });

  it('sorts by name ascending by default and toggles the active header direction', async () => {
    await createPage();
    showInactive();
    expect(rowNames()).toEqual(['Jan Kowalski', 'Anna Nowak']);

    (fixture.nativeElement.querySelector('.sort-button') as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(rowNames()).toEqual(['Anna Nowak', 'Jan Kowalski']);
    expect(api.getAll).toHaveBeenCalledTimes(1);
  });

  it('sorts by birth year and gender using local sorting', async () => {
    await createPage();
    showInactive();

    const sortButtons = Array.from(
      fixture.nativeElement.querySelectorAll('.sort-button'),
    ) as HTMLButtonElement[];
    sortButtons[1].click();
    fixture.detectChanges();
    expect(rowNames()).toEqual(['Jan Kowalski', 'Anna Nowak']);

    sortButtons[2].click();
    fixture.detectChanges();
    expect(rowNames()).toEqual(['Anna Nowak', 'Jan Kowalski']);
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

  it('explains an alias conflict without activating the athlete', async () => {
    const changeStatus = vi.fn(() => throwError(() => new HttpErrorResponse({
      status: 409, error: { title: 'Athlete alias conflict' },
    })));
    await createPage(undefined, { changeStatus });
    showInactive();
    const toggles = Array.from(fixture.nativeElement.querySelectorAll('.status-toggle')) as HTMLButtonElement[];
    toggles.find((toggle) => toggle.getAttribute('aria-checked') === 'false')?.click();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.status-error')?.textContent)
      .toContain('Nie można aktywować zawodnika');
    expect(toggles.find((toggle) => toggle.getAttribute('aria-checked') === 'false')).toBeTruthy();
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

  it('renders the athlete group and keeps the status switch beside the edit action', async () => {
    await createPage();

    const headings = Array.from(
      fixture.nativeElement.querySelectorAll('thead th'),
    ).map((heading) => {
      const cell = heading as HTMLTableCellElement;
      const sortButton = cell.querySelector('.sort-button');
      return sortButton
        ? sortButton.childNodes[0]?.textContent?.trim() ?? ''
        : cell.textContent?.trim() ?? '';
    });
    expect(headings).toEqual(['Zawodnik', 'Rok urodzenia', 'Płeć', 'Grupa treningowa', '']);
    expect(fixture.nativeElement.querySelectorAll('tbody tr:first-child td')).toHaveLength(5);
    expect(fixture.nativeElement.querySelector('tbody tr:first-child td:last-child .status-toggle')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('tbody tr:first-child td:last-child .edit-button')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('.button--primary')?.textContent).toContain(
      'Dodaj zawodnika',
    );
    expect((fixture.nativeElement.querySelector('.button--primary') as HTMLButtonElement).disabled).toBe(false);
    expect((fixture.nativeElement.querySelector('.edit-button') as HTMLButtonElement).disabled).toBe(false);
    expect(fixture.nativeElement.textContent).toContain('Mężczyzna');
  });

  it('opens the shared create form without an additional athlete list request', async () => {
    await createPage();
    openCreate();

    expect(fixture.nativeElement.querySelector('app-athlete-form-modal')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('#modal-title')?.textContent).toContain('Dodaj zawodnika');
    expect(fixture.nativeElement.querySelectorAll('#athlete-form .field-control')).toHaveLength(5);
    expect(fixture.nativeElement.querySelector('#athlete-gender')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('[formControlName="active"]')).toBeNull();
    expect(api.getAll).toHaveBeenCalledTimes(1);
    expect(api.createAthlete).not.toHaveBeenCalled();
  });

  it('shows validation messages without submitting invalid values', async () => {
    await createPage();
    openCreate();
    submitAthleteForm();

    expect(api.createAthlete).not.toHaveBeenCalled();
    expect(fixture.nativeElement.querySelectorAll('.field-error').length).toBeGreaterThan(0);
    expect(fixture.nativeElement.querySelector('app-athlete-form-modal')).toBeTruthy();
  });

  it('creates an active athlete and updates the local list without refetching', async () => {
    await createPage();
    openCreate();
    setTextInput('#athlete-first-name', '  Ala ');
    setTextInput('#athlete-last-name', ' Zielińska  ');
    setTextInput('#athlete-birth-year', '2008');
    setFormSelect('#athlete-gender', 'FEMALE');
    submitAthleteForm();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(api.createAthlete).toHaveBeenCalledWith({
      firstName: 'Ala',
      lastName: 'Zielińska',
      birthYear: 2008,
      gender: 'FEMALE',
      aliases: [],
    });
    expect(fixture.nativeElement.querySelector('app-athlete-form-modal')).toBeNull();
    expect(fixture.nativeElement.textContent).toContain('Ala Zielińska');
    expect(fixture.nativeElement.textContent).toContain('Zawodnik został dodany.');
    expect(api.getAll).toHaveBeenCalledTimes(1);
  });

  it('saves added and edited aliases with the athlete and removes discarded aliases', async () => {
    await createPage();
    openCreate();
    setTextInput('#athlete-first-name', 'Ala');
    setTextInput('#athlete-last-name', 'Zielińska');
    setTextInput('#athlete-birth-year', '2008');
    setFormSelect('#athlete-gender', 'FEMALE');
    const add = Array.from(fixture.nativeElement.querySelectorAll('button') as NodeListOf<HTMLButtonElement>)
      .find((button) => button.textContent?.trim() === 'Dodaj alias')!;
    add.click();
    fixture.detectChanges();
    setTextInput('#athlete-alias-0', 'Alka');
    add.click();
    fixture.detectChanges();
    setTextInput('#athlete-alias-1', 'A.');
    (fixture.nativeElement.querySelector('fieldset[formArrayName=aliases] div button') as HTMLButtonElement).click();
    fixture.detectChanges();
    submitAthleteForm();
    expect(api.createAthlete).toHaveBeenCalledWith(expect.objectContaining({ aliases: ['A.'] }));
  });

  it('keeps entered aliases when the backend reports an alias conflict', async () => {
    await createPage(undefined, {
      createAthlete: vi.fn(() => throwError(() => new HttpErrorResponse({
        status: 409, error: { title: 'Athlete alias conflict' },
      }))),
    });
    openCreate();
    setTextInput('#athlete-first-name', 'Ala');
    setTextInput('#athlete-last-name', 'Zielińska');
    setTextInput('#athlete-birth-year', '2008');
    setFormSelect('#athlete-gender', 'FEMALE');
    const add = Array.from(fixture.nativeElement.querySelectorAll('button') as NodeListOf<HTMLButtonElement>)
      .find((button) => button.textContent?.trim() === 'Dodaj alias')!;
    add.click();
    fixture.detectChanges();
    setTextInput('#athlete-alias-0', 'Alka');
    submitAthleteForm();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Alias jest już używany');
    expect((fixture.nativeElement.querySelector('#athlete-alias-0') as HTMLInputElement).value).toBe('Alka');
  });

  it('preserves local query and sorting state after creating an athlete', async () => {
    await createPage();
    showInactive();
    const sortButtons = fixture.nativeElement.querySelectorAll('.sort-button') as NodeListOf<HTMLButtonElement>;
    sortButtons[1].click();
    fixture.detectChanges();
    sortButtons[1].click();
    fixture.detectChanges();
    setSearch('Kow');
    openCreate();
    setTextInput('#athlete-first-name', 'Ala');
    setTextInput('#athlete-last-name', 'Zielińska');
    setTextInput('#athlete-birth-year', '2008');
    setFormSelect('#athlete-gender', 'FEMALE');
    submitAthleteForm();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(rowNames()).toEqual(['Jan Kowalski']);
    expect(fixture.componentInstance.showInactive()).toBe(true);
    expect(fixture.componentInstance.sortColumn()).toBe('birthYear');
    expect(fixture.componentInstance.sortDirection()).toBe('desc');
    expect(api.getAll).toHaveBeenCalledTimes(1);
  });

  it('shows the duplicate athlete error and retains entered values', async () => {
    const createAthlete = vi.fn(() =>
      throwError(
        () =>
          new HttpErrorResponse({
            status: 409,
            error: { title: 'Athlete already exists' },
          }),
      ),
    );
    await createPage(undefined, { createAthlete });
    openCreate();
    setTextInput('#athlete-first-name', 'Jan');
    setTextInput('#athlete-last-name', 'Kowalski');
    setTextInput('#athlete-birth-year', '2010');
    setFormSelect('#athlete-gender', 'MALE');
    submitAthleteForm();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('app-athlete-form-modal')).toBeTruthy();
    expect((fixture.nativeElement.querySelector('#athlete-first-name') as HTMLInputElement).value).toBe('Jan');
    expect(fixture.nativeElement.querySelector('[role="alert"]')?.textContent).toContain(
      'Zawodnik o tym imieniu, nazwisku i roku urodzenia już istnieje.',
    );
  });

  it('shows field validation errors returned by the API and keeps values', async () => {
    const createAthlete = vi.fn(() =>
      throwError(
        () =>
          new HttpErrorResponse({
            status: 400,
            error: { title: 'Invalid request', errors: { firstName: 'must not be blank' } },
          }),
      ),
    );
    await createPage(undefined, { createAthlete });
    openCreate();
    setTextInput('#athlete-first-name', 'Jan');
    setTextInput('#athlete-last-name', 'Kowalski');
    setTextInput('#athlete-birth-year', '2010');
    setFormSelect('#athlete-gender', 'MALE');
    submitAthleteForm();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('#athlete-first-name-error')?.textContent).toContain(
      'Wpisz od 1 do 100 znaków.',
    );
    expect((fixture.nativeElement.querySelector('#athlete-last-name') as HTMLInputElement).value).toBe(
      'Kowalski',
    );
    expect(fixture.nativeElement.querySelector('app-athlete-form-modal')).toBeTruthy();
  });

  it('keeps the modal open and entered values after a general API failure', async () => {
    const createAthlete = vi.fn(() =>
      throwError(() => new HttpErrorResponse({ status: 500 })),
    );
    await createPage(undefined, { createAthlete });
    openCreate();
    setTextInput('#athlete-first-name', 'Jan');
    setTextInput('#athlete-last-name', 'Kowalski');
    setTextInput('#athlete-birth-year', '2010');
    setFormSelect('#athlete-gender', 'MALE');
    submitAthleteForm();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('app-athlete-form-modal')).toBeTruthy();
    expect((fixture.nativeElement.querySelector('#athlete-last-name') as HTMLInputElement).value).toBe(
      'Kowalski',
    );
    expect(fixture.nativeElement.querySelector('[role="alert"]')?.textContent).toContain(
      'Nie udało się dodać zawodnika.',
    );
  });

  it('cancels without saving or making another API request', async () => {
    await createPage();
    openCreate();
    setTextInput('#athlete-first-name', 'Niezapisany');
    (fixture.nativeElement.querySelector('.cancel-button') as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('app-athlete-form-modal')).toBeNull();
    expect(api.createAthlete).not.toHaveBeenCalled();
    expect(api.getAll).toHaveBeenCalledTimes(1);
  });

  it('opens the shared form in EDIT mode with selected athlete values and does not fetch details', async () => {
    await createPage();
    openEdit();

    expect(fixture.nativeElement.querySelector('#modal-title')?.textContent).toContain('Edytuj zawodnika');
    expect((fixture.nativeElement.querySelector('#athlete-first-name') as HTMLInputElement).value).toBe(
      'Jan',
    );
    expect((fixture.nativeElement.querySelector('#athlete-last-name') as HTMLInputElement).value).toBe(
      'Kowalski',
    );
    expect((fixture.nativeElement.querySelector('#athlete-birth-year') as HTMLInputElement).value).toBe(
      '2010',
    );
    expect((fixture.nativeElement.querySelector('#athlete-gender') as HTMLSelectElement).value).toBe(
      'MALE',
    );
    expect(fixture.nativeElement.querySelector('#athlete-form')).toBeTruthy();
    expect(api.getAll).toHaveBeenCalledTimes(1);
    expect(api.updateAthlete).not.toHaveBeenCalled();
  });

  it('updates the selected athlete locally and shows success without reloading', async () => {
    await createPage();
    openEdit();
    setTextInput('#athlete-first-name', 'Janusz');
    setTextInput('#athlete-last-name', 'Nowak');
    setTextInput('#athlete-birth-year', '2012');
    submitAthleteForm();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(api.updateAthlete).toHaveBeenCalledWith(activeAthlete.id, {
      firstName: 'Janusz',
      lastName: 'Nowak',
      birthYear: 2012,
      gender: 'MALE',
      trainingGroupId: 1,
      aliases: [],
    });
    expect(fixture.nativeElement.querySelector('app-athlete-form-modal')).toBeNull();
    expect(fixture.nativeElement.textContent).toContain('Janusz Nowak');
    expect(fixture.nativeElement.textContent).toContain('Zmiany zawodnika zostały zapisane.');
    expect(api.getAll).toHaveBeenCalledTimes(1);
  });

  it('edits an existing alias as part of the athlete update', async () => {
    await createPage(() => of([{ ...activeAthlete, aliases: ['Janek'] }, inactiveAthlete]));
    openEdit();
    expect((fixture.nativeElement.querySelector('#athlete-alias-0') as HTMLInputElement).value).toBe('Janek');
    setTextInput('#athlete-alias-0', 'Jan K.');
    submitAthleteForm();
    expect(api.updateAthlete).toHaveBeenCalledWith(activeAthlete.id,
      expect.objectContaining({ aliases: ['Jan K.'] }));
  });

  it('shows local validation errors in EDIT mode without sending an update', async () => {
    await createPage();
    openEdit();
    setTextInput('#athlete-first-name', '   ');
    submitAthleteForm();

    expect(api.updateAthlete).not.toHaveBeenCalled();
    expect(fixture.nativeElement.querySelector('#athlete-first-name-error')?.textContent).toContain(
      'To pole jest wymagane.',
    );
    expect(fixture.nativeElement.querySelector('app-athlete-form-modal')).toBeTruthy();
  });

  it('shows backend duplicate error and preserves edit values', async () => {
    const updateAthlete = vi.fn(() =>
      throwError(
        () =>
          new HttpErrorResponse({
            status: 409,
            error: { title: 'Athlete already exists' },
          }),
      ),
    );
    await createPage(undefined, { updateAthlete });
    showInactive();
    openEdit();
    setTextInput('#athlete-first-name', 'Anna');
    setTextInput('#athlete-last-name', 'Nowak');
    setTextInput('#athlete-birth-year', '2011');
    setFormSelect('#athlete-gender', 'FEMALE');
    submitAthleteForm();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('app-athlete-form-modal')).toBeTruthy();
    expect((fixture.nativeElement.querySelector('#athlete-first-name') as HTMLInputElement).value).toBe(
      'Anna',
    );
    expect(fixture.nativeElement.querySelector('[role="alert"]')?.textContent).toContain(
      'Zawodnik o tym imieniu, nazwisku i roku urodzenia już istnieje.',
    );
  });

  it('keeps form open and values after update API failure', async () => {
    const updateAthlete = vi.fn(() =>
      throwError(() => new HttpErrorResponse({ status: 500 })),
    );
    await createPage(undefined, { updateAthlete });
    openEdit();
    setTextInput('#athlete-first-name', 'Janusz');
    submitAthleteForm();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('app-athlete-form-modal')).toBeTruthy();
    expect((fixture.nativeElement.querySelector('#athlete-first-name') as HTMLInputElement).value).toBe(
      'Janusz',
    );
    expect(fixture.nativeElement.querySelector('[role="alert"]')?.textContent).toContain(
      'Nie udało się zapisać zmian zawodnika.',
    );
  });

  it('canceling an edit does not save or change the athlete', async () => {
    await createPage();
    openEdit();
    setTextInput('#athlete-first-name', 'Unsaved');
    (fixture.nativeElement.querySelector('.cancel-button') as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('app-athlete-form-modal')).toBeNull();
    expect(fixture.nativeElement.textContent).toContain('Jan Kowalski');
    expect(api.updateAthlete).not.toHaveBeenCalled();
    expect(api.getAll).toHaveBeenCalledTimes(1);
  });

  it('preserves search, inactive filter, and sorting after successful edit', async () => {
    await createPage();
    showInactive();
    const sortButtons = fixture.nativeElement.querySelectorAll('.sort-button') as NodeListOf<HTMLButtonElement>;
    sortButtons[1].click();
    fixture.detectChanges();
    setSearch('Kow');
    openEdit();
    setTextInput('#athlete-first-name', 'Janusz');
    setTextInput('#athlete-last-name', 'Nowak');
    submitAthleteForm();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(rowNames()).toEqual([]);
    expect(fixture.nativeElement.textContent).toContain('Nie znaleziono zawodników');
    expect(fixture.componentInstance.query()).toBe('Kow');
    expect(fixture.componentInstance.showInactive()).toBe(true);
    expect(fixture.componentInstance.sortColumn()).toBe('birthYear');
    expect(api.getAll).toHaveBeenCalledTimes(1);
  });

  it('prevents duplicate edit submissions while the request is pending', async () => {
    const pending = new Subject<AthleteResponse>();
    const updateAthlete = vi.fn(() => pending);
    await createPage(undefined, { updateAthlete });
    openEdit();
    submitAthleteForm();
    fixture.detectChanges();

    const saveButton = fixture.nativeElement.querySelector('.save-button') as HTMLButtonElement;
    expect(updateAthlete).toHaveBeenCalledTimes(1);
    expect(saveButton.disabled).toBe(true);
    saveButton.click();
    expect(updateAthlete).toHaveBeenCalledTimes(1);
  });
});

import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Observable, Subject, of, throwError } from 'rxjs';
import { TestManagementPage } from './test-management.page';
import { TestResponse } from './test.model';
import { TestsApiService } from './tests-api.service';

const activeTest: TestResponse = {
  id: 1,
  name: 'Bieg na 40 m',
  unit: 's',
  category: 'TIME',
  higherIsBetter: false,
  active: true,
};

const inactiveTest: TestResponse = {
  id: 2,
  name: 'Skok w dal',
  unit: 'm',
  category: 'DISTANCE',
  higherIsBetter: true,
  active: false,
};

const speedTest: TestResponse = {
  id: 4,
  name: 'Sprint',
  unit: 'm/s',
  category: 'SPEED',
  higherIsBetter: true,
  active: true,
};

const createdTest: TestResponse = {
  id: 3,
  name: 'Rzut piłką',
  unit: 'm',
  category: 'DISTANCE',
  higherIsBetter: true,
  active: true,
};

describe('TestManagementPage', () => {
  let fixture: ComponentFixture<TestManagementPage>;
  let api: {
    getAll: ReturnType<typeof vi.fn>;
    createTest: ReturnType<typeof vi.fn>;
    updateTest: ReturnType<typeof vi.fn>;
    changeStatus: ReturnType<typeof vi.fn>;
  };

  async function createPage(
    getAll: () => Observable<TestResponse[]> = () => of([activeTest, inactiveTest]),
    overrides: Partial<typeof api> = {},
  ): Promise<void> {
    api = {
      getAll: vi.fn(getAll),
      createTest: vi.fn(() => of(createdTest)),
      updateTest: vi.fn((_id: number, _body: object) => of(activeTest)),
      changeStatus: vi.fn((_id: number, body: { active: boolean }) =>
        of(body.active ? activeTest : inactiveTest),
      ),
      ...overrides,
    };
    await TestBed.configureTestingModule({
      imports: [TestManagementPage],
      providers: [{ provide: TestsApiService, useValue: api }],
    }).compileComponents();
    fixture = TestBed.createComponent(TestManagementPage);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  function setTextInput(selector: string, value: string): void {
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

  function showInactiveForTest(): void {
    const toggle = fixture.nativeElement.querySelector('.inactive-toggle input') as HTMLInputElement;
    toggle.checked = true;
    toggle.dispatchEvent(new Event('change'));
    fixture.detectChanges();
  }

  function visibleTestNames(): string[] {
    return Array.from(fixture.nativeElement.querySelectorAll('tbody tr')).map((row) =>
      (row as HTMLElement).querySelector('td strong')?.textContent?.trim() ?? '',
    );
  }

  function openCreate(): void {
    (fixture.nativeElement.querySelector('.page-heading .button--primary') as HTMLButtonElement).click();
    fixture.detectChanges();
  }

  function openEdit(): void {
    (fixture.nativeElement.querySelector('.edit-button') as HTMLButtonElement).click();
    fixture.detectChanges();
  }

  function submitForm(): void {
    (fixture.nativeElement.querySelector('.save-button') as HTMLButtonElement).click();
    fixture.detectChanges();
  }

  it('loads the complete list once and displays only active tests by default', async () => {
    await createPage();

    expect(api.getAll).toHaveBeenCalledTimes(1);
    expect(fixture.nativeElement.textContent).toContain('Bieg na 40 m');
    expect(fixture.nativeElement.textContent).not.toContain('Skok w dal');
  });

  it('filters inactive tests locally without an additional request', async () => {
    await createPage();

    const toggle = fixture.nativeElement.querySelector('.inactive-toggle input') as HTMLInputElement;
    toggle.checked = true;
    toggle.dispatchEvent(new Event('change'));
    fixture.detectChanges();

    expect(api.getAll).toHaveBeenCalledTimes(1);
    expect(fixture.nativeElement.textContent).toContain('Skok w dal');
    expect(fixture.nativeElement.querySelectorAll('.status-toggle')).toHaveLength(2);
    expect(fixture.nativeElement.querySelector('.status-toggle')?.getAttribute('aria-checked')).toBe(
      'true',
    );
  });

  it('searches locally, ignoring case and whitespace without an additional request', async () => {
    await createPage();
    setTextInput('input[type="search"]', '  BIEG  ');

    expect(fixture.nativeElement.textContent).toContain('Bieg na 40 m');
    expect(fixture.nativeElement.textContent).not.toContain('Skok w dal');
    setTextInput('input[type="search"]', '');
    expect(fixture.nativeElement.textContent).toContain('Bieg na 40 m');
    expect(api.getAll).toHaveBeenCalledTimes(1);
  });

  it('sorts by name ascending by default and toggles to descending', async () => {
    await createPage(() => of([speedTest, inactiveTest, activeTest]));
    const toggle = fixture.nativeElement.querySelector('.inactive-toggle input') as HTMLInputElement;
    toggle.checked = true;
    toggle.dispatchEvent(new Event('change'));
    fixture.detectChanges();

    expect(visibleTestNames()).toEqual(['Bieg na 40 m', 'Skok w dal', 'Sprint']);
    const nameSort = fixture.nativeElement.querySelector('thead th button') as HTMLButtonElement;
    nameSort.click();
    fixture.detectChanges();

    expect(visibleTestNames()).toEqual(['Sprint', 'Skok w dal', 'Bieg na 40 m']);
    expect(nameSort.closest('th')?.getAttribute('aria-sort')).toBe('descending');
    expect(api.getAll).toHaveBeenCalledTimes(1);
  });

  it('sorts by localized category names', async () => {
    await createPage(() => of([speedTest, inactiveTest, activeTest]));
    showInactiveForTest();
    const sortButtons = Array.from(
      fixture.nativeElement.querySelectorAll('thead th button'),
    ) as HTMLButtonElement[];
    const categorySort = sortButtons.find((button) => button.textContent?.includes('Kategoria'))!;
    categorySort.click();
    fixture.detectChanges();

    expect(visibleTestNames()).toEqual(['Bieg na 40 m', 'Skok w dal', 'Sprint']);
    expect(
      Array.from(fixture.nativeElement.querySelectorAll('tbody tr td:nth-child(2)')).map(
        (cell) => (cell as HTMLElement).textContent?.trim(),
      ),
    ).toEqual(['Czas', 'Dystans', 'Prędkość']);
    expect(categorySort.closest('th')?.getAttribute('aria-sort')).toBe('ascending');
    categorySort.click();
    fixture.detectChanges();
    expect(
      Array.from(fixture.nativeElement.querySelectorAll('tbody tr td:nth-child(2)')).map(
        (cell) => (cell as HTMLElement).textContent?.trim(),
      ),
    ).toEqual(['Prędkość', 'Dystans', 'Czas']);
    expect(categorySort.closest('th')?.getAttribute('aria-sort')).toBe('descending');
    expect(api.getAll).toHaveBeenCalledTimes(1);
  });

  it('filters locally by each supported category', async () => {
    await createPage(() => of([speedTest, inactiveTest, activeTest]));
    showInactiveForTest();
    const categoryFilter = 'select[aria-label="Filtruj według kategorii"]';

    setSelect(categoryFilter, 'TIME');
    expect(visibleTestNames()).toEqual(['Bieg na 40 m']);
    expect(fixture.nativeElement.querySelector('tbody tr td:nth-child(2)')?.textContent).toContain(
      'Czas',
    );

    setSelect(categoryFilter, 'DISTANCE');
    expect(visibleTestNames()).toEqual(['Skok w dal']);
    expect(fixture.nativeElement.querySelector('tbody tr td:nth-child(2)')?.textContent).toContain(
      'Dystans',
    );

    setSelect(categoryFilter, 'SPEED');
    expect(visibleTestNames()).toEqual(['Sprint']);
    expect(fixture.nativeElement.querySelector('tbody tr td:nth-child(2)')?.textContent).toContain(
      'Prędkość',
    );
    expect(api.getAll).toHaveBeenCalledTimes(1);
  });

  it('combines category filter, name search and inactive toggle without requests', async () => {
    await createPage(() => of([speedTest, inactiveTest, activeTest]));
    const categoryFilter = 'select[aria-label="Filtruj według kategorii"]';
    setSelect(categoryFilter, 'DISTANCE');
    setTextInput('input[type="search"]', ' skok ');
    expect(visibleTestNames()).toEqual([]);

    showInactiveForTest();
    expect(visibleTestNames()).toEqual(['Skok w dal']);

    setTextInput('input[type="search"]', 'niepasujące');
    expect(visibleTestNames()).toEqual([]);
    expect(api.getAll).toHaveBeenCalledTimes(1);
  });

  it('opens the shared modal in CREATE mode with empty and default values', async () => {
    await createPage();
    openCreate();

    expect(fixture.nativeElement.querySelector('#modal-title')?.textContent).toContain('Dodaj test');
    expect((fixture.nativeElement.querySelector('#test-name') as HTMLInputElement).value).toBe('');
    expect((fixture.nativeElement.querySelector('#test-unit') as HTMLSelectElement).value).toBe('');
    expect((fixture.nativeElement.querySelector('#test-category') as HTMLSelectElement).value).toBe('');
    expect(
      (fixture.nativeElement.querySelector('#test-higher-is-better') as HTMLInputElement).checked,
    ).toBe(false);
    expect(fixture.nativeElement.querySelector('#test-active')).toBeNull();
  });

  it('uses the shared button hierarchy and communicates row status as a switch', async () => {
    await createPage();

    const addButton = fixture.nativeElement.querySelector('.page-heading button') as HTMLButtonElement;
    const statusToggle = fixture.nativeElement.querySelector('.status-toggle') as HTMLButtonElement;
    const editButton = fixture.nativeElement.querySelector('.edit-button') as HTMLButtonElement;
    expect(addButton.classList.contains('button--primary')).toBe(true);
    expect(statusToggle.getAttribute('role')).toBe('switch');
    expect(statusToggle.getAttribute('aria-checked')).toBe('true');
    expect(statusToggle.getAttribute('aria-label')).toContain(activeTest.name);
    expect(statusToggle.textContent).toContain('Aktywny');
    expect(statusToggle.firstElementChild?.textContent).toContain('Aktywny');
    expect(statusToggle.lastElementChild?.classList.contains('status-toggle__track')).toBe(true);
    expect(statusToggle.nextElementSibling).toBe(editButton);
    expect(editButton.classList.contains('button--secondary')).toBe(true);
    expect(editButton.querySelector('svg')).toBeNull();
    expect(fixture.nativeElement.querySelector('.search-filter > span:first-child')?.textContent).toBe('Szukaj testu');
    expect(fixture.nativeElement.querySelector('.category-filter > span')?.textContent).toBe('Kategoria');
    expect(fixture.nativeElement.querySelector('.inactive-filter > span')?.textContent).toBe('Pokaż nieaktywne');
    expect(statusToggle.getAttribute('aria-checked')).toBe('true');
    expect(
      (fixture.nativeElement.querySelector('.inactive-toggle input') as HTMLInputElement).getAttribute('aria-labelledby'),
    ).toBe('inactive-filter-label');

    openCreate();
    expect(
      (fixture.nativeElement.querySelector('.cancel-button') as HTMLButtonElement).classList.contains(
        'button--secondary',
      ),
    ).toBe(true);
    expect(
      (fixture.nativeElement.querySelector('.save-button') as HTMLButtonElement).classList.contains(
        'button--primary',
      ),
    ).toBe(true);
  });

  it('shows validation errors and sends no create request for an invalid form', async () => {
    await createPage();
    openCreate();
    submitForm();

    expect(api.createTest).not.toHaveBeenCalled();
    expect(fixture.nativeElement.querySelectorAll('.field-error').length).toBeGreaterThan(0);
    expect(fixture.nativeElement.querySelector('app-test-form-modal')).toBeTruthy();
  });

  it('creates a test and updates the local list after success', async () => {
    await createPage();
    openCreate();
    setTextInput('#test-name', 'Rzut piłką');
    setSelect('#test-unit', 'm');
    setSelect('#test-category', 'DISTANCE');
    (fixture.nativeElement.querySelector('#test-higher-is-better') as HTMLInputElement).click();
    fixture.detectChanges();
    submitForm();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(api.createTest).toHaveBeenCalledWith({
      name: 'Rzut piłką',
      unit: 'm',
      category: 'DISTANCE',
      higherIsBetter: true,
    });
    expect(fixture.nativeElement.querySelector('app-test-form-modal')).toBeNull();
    expect(fixture.nativeElement.textContent).toContain('Rzut piłką');
    expect(api.getAll).toHaveBeenCalledTimes(1);
  });

  it('preserves form values and keeps the modal open after an API error', async () => {
    const createTest = vi.fn(() =>
      throwError(
        () =>
          new HttpErrorResponse({
            status: 409,
            error: { title: 'Test already exists' },
          }),
      ),
    );
    await createPage(undefined, { createTest });
    openCreate();
    setTextInput('#test-name', 'Bieg powtórzony');
    setSelect('#test-unit', 's');
    setSelect('#test-category', 'TIME');
    submitForm();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('app-test-form-modal')).toBeTruthy();
    expect((fixture.nativeElement.querySelector('#test-name') as HTMLInputElement).value).toBe(
      'Bieg powtórzony',
    );
    expect(fixture.nativeElement.querySelector('[role="alert"]')?.textContent).toContain(
      'Test o tej nazwie już istnieje.',
    );
  });

  it('prevents duplicate submissions while a save is in progress', async () => {
    const pendingSave = new Subject<TestResponse>();
    const createTest = vi.fn(() => pendingSave);
    await createPage(undefined, { createTest });
    openCreate();
    setTextInput('#test-name', 'Rzut piłką');
    setSelect('#test-unit', 'm');
    setSelect('#test-category', 'DISTANCE');
    submitForm();
    fixture.detectChanges();

    const saveButton = fixture.nativeElement.querySelector('.save-button') as HTMLButtonElement;
    expect(api.createTest).toHaveBeenCalledTimes(1);
    expect(saveButton.disabled).toBe(true);
    expect((fixture.nativeElement.querySelector('.cancel-button') as HTMLButtonElement).disabled).toBe(
      true,
    );
    saveButton.click();
    expect(api.createTest).toHaveBeenCalledTimes(1);
  });

  it('cancels the modal without changing the list', async () => {
    await createPage();
    openCreate();
    (fixture.nativeElement.querySelector('.cancel-button') as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('app-test-form-modal')).toBeNull();
    expect(fixture.nativeElement.textContent).not.toContain(createdTest.name);
    expect(api.createTest).not.toHaveBeenCalled();
  });

  it('opens the same form in EDIT mode with selected test values', async () => {
    await createPage();
    openEdit();

    expect(fixture.nativeElement.querySelector('#modal-title')?.textContent).toContain('Edytuj test');
    expect((fixture.nativeElement.querySelector('#test-name') as HTMLInputElement).value).toBe(
      activeTest.name,
    );
    expect((fixture.nativeElement.querySelector('#test-unit') as HTMLSelectElement).value).toBe('s');
    expect((fixture.nativeElement.querySelector('#test-category') as HTMLSelectElement).value).toBe(
      'TIME',
    );
    expect(fixture.nativeElement.querySelector('#test-active')).toBeNull();
  });

  it('updates the test through PUT and updates the local collection', async () => {
    const updatedTest = { ...activeTest, name: 'Bieg na 50 m' };
    const updateTest = vi.fn(() => of(updatedTest));
    await createPage(undefined, { updateTest });
    openEdit();
    setTextInput('#test-name', 'Bieg na 50 m');
    submitForm();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(updateTest).toHaveBeenCalledWith(1, {
      name: 'Bieg na 50 m',
      unit: 's',
      category: 'TIME',
      higherIsBetter: false,
    });
    expect(fixture.nativeElement.textContent).toContain('Bieg na 50 m');
    expect(fixture.nativeElement.textContent).not.toContain(activeTest.name);
    expect(fixture.nativeElement.querySelector('app-test-form-modal')).toBeNull();
  });

  it('keeps entered edit values after an API error', async () => {
    const updateTest = vi.fn(() =>
      throwError(() => new HttpErrorResponse({ status: 409, error: { title: 'Test already exists' } })),
    );
    await createPage(undefined, { updateTest });
    openEdit();
    setTextInput('#test-name', 'Changed name');
    submitForm();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('app-test-form-modal')).toBeTruthy();
    expect((fixture.nativeElement.querySelector('#test-name') as HTMLInputElement).value).toBe(
      'Changed name',
    );
  });

  it('updates status from the row and reapplies the active-only filter', async () => {
    const changeStatus = vi.fn((_id: number, request: { active: boolean }) =>
      of({ ...activeTest, active: request.active }),
    );
    await createPage(undefined, { changeStatus });

    const statusButton = fixture.nativeElement.querySelector('.status-toggle') as HTMLButtonElement;
    expect(statusButton.textContent.trim()).toContain('Aktywny');
    expect(statusButton.getAttribute('aria-checked')).toBe('true');
    statusButton.click();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(changeStatus).toHaveBeenCalledWith(1, { active: false });
    expect(fixture.nativeElement.textContent).not.toContain(activeTest.name);
    expect(api.getAll).toHaveBeenCalledTimes(1);
  });

  it('activates an inactive test with the matching action label', async () => {
    await createPage();
    const toggle = fixture.nativeElement.querySelector('.inactive-toggle input') as HTMLInputElement;
    toggle.checked = true;
    toggle.dispatchEvent(new Event('change'));
    fixture.detectChanges();

    const buttons = Array.from(
      fixture.nativeElement.querySelectorAll('.status-toggle'),
    ) as HTMLButtonElement[];
    expect(buttons.map((button) => button.textContent.trim())).toEqual(['Aktywny', 'Nieaktywny']);
    expect(buttons.map((button) => button.getAttribute('aria-checked'))).toEqual(['true', 'false']);
    buttons[1].click();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(api.changeStatus).toHaveBeenCalledWith(2, { active: true });
    expect(api.getAll).toHaveBeenCalledTimes(1);
  });

  it('shows a status API error without changing the test', async () => {
    const changeStatus = vi.fn(() =>
      throwError(() => new HttpErrorResponse({ status: 500 })),
    );
    await createPage(undefined, { changeStatus });
    (fixture.nativeElement.querySelector('.status-toggle') as HTMLButtonElement).click();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.status-error')?.textContent).toContain(
      'Nie udało się zmienić statusu testu.',
    );
    expect(fixture.nativeElement.textContent).toContain(activeTest.name);
    expect(api.getAll).toHaveBeenCalledTimes(1);
  });

  it('retries loading the list after an API error', async () => {
    let attempts = 0;
    const getAll = () =>
      ++attempts === 1
        ? throwError(() => new HttpErrorResponse({ status: 500 }))
        : of([activeTest, inactiveTest]);
    await createPage(getAll);

    expect(fixture.nativeElement.querySelector('.feedback.error')?.textContent).toContain(
      'Nie udało się pobrać listy testów.',
    );
    (fixture.nativeElement.querySelector('.retry-button') as HTMLButtonElement).click();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(api.getAll).toHaveBeenCalledTimes(2);
    expect(fixture.nativeElement.textContent).toContain(activeTest.name);
  });

  it('preserves loading and retry behavior', async () => {
    const pending = new Subject<TestResponse[]>();
    await createPage(() => pending);
    expect(fixture.nativeElement.querySelector('[role="status"]')?.textContent).toContain(
      'Ładowanie testów',
    );
    pending.next([activeTest]);
    pending.complete();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain(activeTest.name);
  });
});

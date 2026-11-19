import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { signal } from '@angular/core';
import { Observable, Subject, of, throwError } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { TrainingListItem } from './training.model';
import { TrainingsApiService } from './trainings-api.service';
import { TrainingsPage } from './trainings.page';
import { TrainingImportsApiService } from '../training-imports/training-imports-api.service';
import { TrainingImportListItem, TrainingImportStatusResponse } from '../training-imports/training-import.model';

const trainings: TrainingListItem[] = [
  {
    id: 1,
    name: 'Trening wytrzymałościowy',
    trainingDate: '2026-11-10',
    trainingGroup: { id: 2, name: 'Średni dystans' },
    definitionCount: 2,
    participantCount: 5,
    status: 'PLANNED',
    trainingType: 'TEMPO',
    assignmentType: 'GROUP',
    assignedAthleteCount: 0,
  },
  {
    id: 2,
    name: 'Sprint',
    trainingDate: '2026-11-12',
    trainingGroup: { id: 1, name: 'Sprint' },
    definitionCount: 1,
    participantCount: 0,
    status: 'PLANNED',
    trainingType: 'TEMPO',
    assignmentType: 'GROUP',
    assignedAthleteCount: 0,
  },
];

const completedTraining: TrainingListItem = {
  id: 3,
  name: 'Trening techniczny',
  trainingDate: '2026-11-09',
  trainingGroup: { id: 1, name: 'Sprint' },
  definitionCount: 1,
  participantCount: 2,
  status: 'COMPLETED',
  trainingType: 'TEMPO',
  assignmentType: 'GROUP',
  assignedAthleteCount: 0,
};

const freeTextTraining: TrainingListItem = {
  id: 4,
  name: 'Trening techniczny',
  trainingDate: '2026-11-13',
  trainingGroup: { id: 1, name: 'Sprint' },
  definitionCount: 0,
  participantCount: 0,
  status: 'PLANNED',
  trainingType: 'FREE_TEXT',
  assignmentType: 'GROUP',
  assignedAthleteCount: 0,
};

const pendingImport: TrainingImportListItem = {
  id: 21,
  trainingGroupId: 1,
  trainingGroupName: 'Sprint',
  trainingType: 'TEMPO',
  status: 'PENDING',
  nextRetryAt: null,
  createdAt: '2026-11-12T10:00:00Z',
  possibleDuplicate: true,
  name: null,
  trainingDate: null,
  failureMessage: null,
};

describe('TrainingsPage', () => {
  let fixture: ComponentFixture<TrainingsPage>;
  let api: {
    getAll: ReturnType<typeof vi.fn>;
    deleteTraining: ReturnType<typeof vi.fn>;
    bulkDelete: ReturnType<typeof vi.fn>;
    bulkComplete: ReturnType<typeof vi.fn>;
  };
  let importsApi: {
    getActive: ReturnType<typeof vi.fn>;
    retry: ReturnType<typeof vi.fn>;
  };

  async function createPage(
    getAll: () => Observable<TrainingListItem[]> = () => of(trainings),
    waitForResponse = true,
    getImports: () => Observable<TrainingImportListItem[]> = () => of([]),
  ): Promise<void> {
    api = {
      getAll: vi.fn(getAll),
      deleteTraining: vi.fn(() => of(void 0)),
      bulkDelete: vi.fn(() => of(void 0)),
      bulkComplete: vi.fn(() => of({ completedIds: [], alreadyCompletedIds: [], skippedUndatedIds: [] })),
    };
    importsApi = {
      getActive: vi.fn(getImports),
      retry: vi.fn(() => of({})),
    };
    await TestBed.configureTestingModule({
      imports: [TrainingsPage],
      providers: [
        provideRouter([]),
        { provide: TrainingsApiService, useValue: api },
        { provide: TrainingImportsApiService, useValue: importsApi },
        {
          provide: AuthService,
          useValue: {
            currentUser: signal({
              authenticated: true,
              userId: 1,
              username: 'coach',
              role: 'COACH',
              athleteId: null,
            }),
          },
        },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(TrainingsPage);
    fixture.detectChanges();
    if (waitForResponse) {
      await fixture.whenStable();
      fixture.detectChanges();
    }
  }

  function setSearch(value: string): void {
    const input = fixture.nativeElement.querySelector('input[type="search"]') as HTMLInputElement;
    input.value = value;
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  }

  function visibleNames(): string[] {
    return Array.from(fixture.nativeElement.querySelectorAll('tbody tr')).map(
      (row) => (row as HTMLElement).querySelector('td strong')?.textContent?.trim() ?? '',
    );
  }

  it('shows bulk actions directly above the table only while trainings are selected', async () => {
    await createPage();
    expect(fixture.nativeElement.querySelector('.bulk-actions')).toBeNull();
    const checkbox = fixture.nativeElement.querySelector('[aria-label="Zaznacz trening: Sprint"]') as HTMLInputElement;
    checkbox.click();
    fixture.detectChanges();
    const actions = fixture.nativeElement.querySelector('.bulk-actions') as HTMLElement;
    expect(actions.textContent).toContain('Zaznaczone: 1');
    expect(actions.textContent).toContain('Akcje');
    expect(actions.nextElementSibling?.classList.contains('table-surface')).toBe(true);

    checkbox.click();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.bulk-actions')).toBeNull();
  });

  it('selects and deselects visible trainings without changing hidden selections', async () => {
    await createPage();
    const header = fixture.nativeElement.querySelector('#select-visible-trainings') as HTMLInputElement;
    expect(header.checked).toBe(false);
    expect(header.indeterminate).toBe(false);
    const first = fixture.nativeElement.querySelector('[aria-label="Zaznacz trening: Sprint"]') as HTMLInputElement;
    first.click();
    fixture.detectChanges();
    expect(header.indeterminate).toBe(true);
    header.click();
    fixture.detectChanges();
    expect([...fixture.componentInstance.selectedIds()].sort()).toEqual([1, 2]);
    expect(header.checked).toBe(true);
    expect(header.indeterminate).toBe(false);
    setSearch('ŚREDNI');
    expect(header.checked).toBe(true);
    header.click();
    fixture.detectChanges();
    expect(fixture.componentInstance.selectedIds()).toEqual([2]);
    expect(header.checked).toBe(false);
    expect(header.indeterminate).toBe(false);
    setSearch('');
    expect(header.indeterminate).toBe(true);
    header.click();
    fixture.detectChanges();
    expect([...fixture.componentInstance.selectedIds()].sort()).toEqual([1, 2]);
    fixture.componentInstance.actionsOpen.set(true);
    header.click();
    fixture.detectChanges();
    expect(fixture.componentInstance.selectedIds()).toEqual([]);
    expect(fixture.componentInstance.actionsOpen()).toBe(false);
    expect(fixture.nativeElement.querySelector('.bulk-actions')).toBeNull();
  });

  it('does not select import rows and disables the header when no trainings are visible', async () => {
    await createPage(() => of(trainings), true, () => of([pendingImport]));
    setSearch('no matching training');
    const header = fixture.nativeElement.querySelector('#select-visible-trainings') as HTMLInputElement;
    expect(header.disabled).toBe(true);
    header.click();
    expect(fixture.componentInstance.selectedIds()).toEqual([]);
    setSearch('');
    expect(header.disabled).toBe(false);
    header.click();
    fixture.detectChanges();
    expect([...fixture.componentInstance.selectedIds()].sort()).toEqual([1, 2]);
    expect(fixture.nativeElement.querySelector('.import-row input[type="checkbox"]')).toBeNull();
  });

  it('disables and clears the header state while stale trainings are loading or errored', async () => {
    await createPage(() => of(trainings), true, () => of([pendingImport]));
    const page = fixture.componentInstance;
    page.selectedIds.set([1, 2]);
    fixture.detectChanges();
    const header = fixture.nativeElement.querySelector('#select-visible-trainings') as HTMLInputElement;
    expect(header.checked).toBe(true);

    page.loading.set(true);
    fixture.detectChanges();
    expect(header.disabled).toBe(true);
    expect(header.checked).toBe(false);
    expect(header.indeterminate).toBe(false);
    header.click();
    expect(page.selectedIds()).toEqual([1, 2]);

    page.loading.set(false);
    page.errorMessage.set('Błąd');
    fixture.detectChanges();
    expect(header.disabled).toBe(true);
    expect(header.checked).toBe(false);
    expect(header.indeterminate).toBe(false);
    expect(page.selectedIds()).toEqual([1, 2]);

    page.errorMessage.set(null);
    fixture.detectChanges();
    expect(header.disabled).toBe(false);
    expect(header.checked).toBe(true);
  });

  it('retains hidden selections and completes only after an explicit action', async () => {
    await createPage();
    const first = fixture.nativeElement.querySelector('[aria-label="Zaznacz trening: Sprint"]') as HTMLInputElement;
    first.click();
    fixture.detectChanges();
    setSearch('ŚREDNI');
    expect(fixture.componentInstance.selectedIds()).toEqual([2]);
    expect(fixture.nativeElement.textContent).toContain('Zaznaczone: 1');
    setSearch('nieznany');
    expect(fixture.nativeElement.querySelector('.bulk-actions')).toBeTruthy();
    expect(api.bulkComplete).not.toHaveBeenCalled();
    api.bulkComplete.mockReturnValue(of({ completedIds: [], alreadyCompletedIds: [], skippedUndatedIds: [2] }));
    fixture.componentInstance.completeSelected();
    fixture.detectChanges();
    expect(api.bulkComplete).toHaveBeenCalledWith([2]);
    expect(fixture.componentInstance.selectedIds()).toEqual([]);
    expect(fixture.nativeElement.textContent).toContain('Bez daty, pominięto: Sprint');
  });

  it('does not expose write actions to athletes', async () => {
    await createPage();
    const auth = TestBed.inject(AuthService);
    auth.currentUser.set({ authenticated: true, userId: 2, username: 'athlete', role: 'ATHLETE', athleteId: 1 });
    fixture.componentInstance.selectedIds.set([1]);
    fixture.componentInstance.actionsOpen.set(true);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Drukuj');
    expect(fixture.nativeElement.querySelector('.bulk-delete-button')).toBeNull();
    expect(fixture.nativeElement.textContent).not.toContain('Oznacz jako ukończony');
  });

  it('sorts undated sessions first and excludes them from date ranges', async () => {
    const undated: TrainingListItem = { ...trainings[0], id: 8, name: 'Bez terminu', trainingDate: null };
    await createPage(() => of([...trainings, undated]));
    const page = fixture.componentInstance;
    expect(page.visibleTrainings()[0].id).toBe(8);
    expect(fixture.nativeElement.textContent).toContain('Bez daty');
    page.sortDirection.set('asc');
    expect(page.visibleTrainings()[0].id).toBe(8);
    page.trainingDateFrom.set('2026-11-01');
    expect(page.visibleTrainings().some((training) => training.id === 8)).toBe(false);
  });

  it('shows loading while the API request is pending', async () => {
    const response = new Subject<TrainingListItem[]>();
    await createPage(() => response, false);

    expect(fixture.nativeElement.querySelector('[role="status"]')?.textContent).toContain('Ładowanie');
    response.next(trainings);
    response.complete();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(visibleNames()).toHaveLength(2);
  });

  it('shows the empty state when the API returns no trainings', async () => {
    await createPage(() => of([]));

    expect(fixture.nativeElement.querySelector('[role="status"]')?.textContent).toContain(
      'Brak treningów do wyświetlenia',
    );
  });

  it('shows an error and retries the list request', async () => {
    let attempt = 0;
    await createPage(() => {
      attempt += 1;
      return attempt === 1
        ? throwError(() => new HttpErrorResponse({ status: 500 }))
        : of(trainings);
    });

    expect(fixture.nativeElement.querySelector('[role="alert"]')?.textContent).toContain(
      'Nie udało się pobrać listy treningów',
    );
    (fixture.nativeElement.querySelector('.retry-button') as HTMLButtonElement).click();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(api.getAll).toHaveBeenCalledTimes(2);
    expect(visibleNames()).toHaveLength(2);
  });

  it('displays response fields, including a zero participant count, in newest-first order', async () => {
    await createPage();
    const region = fixture.nativeElement.querySelector('.table-surface[role="region"]') as HTMLElement;
    expect(region.getAttribute('aria-label')).toBe('Lista treningów');
    expect(region.getAttribute('tabindex')).toBe('0');

    expect(visibleNames()).toEqual(['Sprint', 'Trening wytrzymałościowy']);
    expect(fixture.nativeElement.textContent).toContain('Średni dystans');
    expect(fixture.nativeElement.textContent).toContain('12.11.2026');
    expect(fixture.nativeElement.textContent).toContain('0');
    expect(fixture.nativeElement.querySelector('a[href="/trainings/2"]')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('a[href="/trainings/2/edit"]')?.textContent).toContain(
      'Edytuj',
    );
    expect(fixture.nativeElement.querySelector('a[href="/trainings/2/complete"]')?.textContent)
      .toContain('Uzupełnij trening');
    expect(fixture.nativeElement.querySelector('a[href="/trainings/new"]')?.textContent).toContain(
      'Dodaj trening',
    );
  });

  it('shows all statuses by default and filters planned and completed trainings', async () => {
    await createPage(() => of([...trainings, completedTraining]));

    expect(visibleNames()).toHaveLength(3);
    const badges = [...fixture.nativeElement.querySelectorAll('.status-badge')] as HTMLElement[];
    expect(badges.some((badge) => badge.textContent.trim() === 'Planowany' &&
      !badge.classList.contains('status-badge--completed'))).toBe(true);
    expect(badges.some((badge) => badge.textContent.trim() === 'Zakończony' &&
      badge.classList.contains('status-badge--completed'))).toBe(true);

    const statusFilter = fixture.nativeElement.querySelector('[aria-label="Filtruj według statusu"]') as HTMLSelectElement;
    statusFilter.value = 'COMPLETED';
    statusFilter.dispatchEvent(new Event('change'));
    fixture.detectChanges();

    expect(visibleNames()).toEqual(['Trening techniczny']);
    expect(fixture.nativeElement.querySelector('.status-badge')?.textContent.trim()).toBe('Zakończony');
    expect(fixture.nativeElement.querySelector('.edit-training-link')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('.complete-training-link')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('.delete-training-button')).toBeNull();

    statusFilter.value = 'PLANNED';
    statusFilter.dispatchEvent(new Event('change'));
    fixture.detectChanges();
    expect(visibleNames()).toHaveLength(2);

    statusFilter.value = '';
    statusFilter.dispatchEvent(new Event('change'));
    fixture.detectChanges();
    expect(visibleNames()).toHaveLength(3);
  });

  it('displays both training types and filters the unified diary by type', async () => {
    await createPage(() => of([...trainings, freeTextTraining]));

    expect(visibleNames()).toHaveLength(3);
    expect(fixture.nativeElement.textContent).toContain('Trening ogólny');
    const typeFilter = fixture.nativeElement.querySelector('[aria-label="Filtruj według rodzaju treningu"]') as HTMLSelectElement;
    typeFilter.value = 'FREE_TEXT';
    typeFilter.dispatchEvent(new Event('change'));
    fixture.detectChanges();

    expect(visibleNames()).toEqual(['Trening techniczny']);
    expect(fixture.nativeElement.querySelector('.edit-training-link')?.getAttribute('href'))
      .toBe('/trainings/4/free-text/edit');
    expect(fixture.nativeElement.querySelector('.complete-training-link')).toBeNull();
    expect(fixture.nativeElement.querySelector('[data-label="Liczba uczestników"]')?.textContent?.trim()).toBe('—');

    typeFilter.value = '';
    typeFilter.dispatchEvent(new Event('change'));
    fixture.detectChanges();
    expect(visibleNames()).toHaveLength(3);
  });

  it('requires explicit confirmation and leaves a training unchanged when cancelled', async () => {
    await createPage(() => of([...trainings, completedTraining]));
    const statusFilter = fixture.nativeElement.querySelector('[aria-label="Filtruj według statusu"]') as HTMLSelectElement;
    statusFilter.value = 'COMPLETED';
    statusFilter.dispatchEvent(new Event('change'));
    fixture.detectChanges();
    fixture.componentInstance.selectedIds.set([completedTraining.id]);
    fixture.componentInstance.openDeletionConfirmation();
    fixture.detectChanges();

    const dialog = fixture.nativeElement.querySelector('app-training-delete-confirmation');
    expect(dialog.textContent).toContain('trwale usunąć');
    expect(dialog.textContent).toContain('wszystkie zapisane wyniki');
    expect(dialog.textContent).toContain('nie można cofnąć');
    (dialog.querySelector('.button--secondary') as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('app-training-delete-confirmation')).toBeNull();
    expect(api.bulkDelete).not.toHaveBeenCalled();
    expect(visibleNames()).toEqual(['Trening techniczny']);
  });

  it('deletes only after explicit confirmation and removes the training from the list', async () => {
    let remaining = [...trainings, completedTraining];
    await createPage(() => of(remaining));
    api.bulkDelete.mockImplementation(() => { remaining = trainings; return of(void 0); });
    const statusFilter = fixture.nativeElement.querySelector('[aria-label="Filtruj według statusu"]') as HTMLSelectElement;
    statusFilter.value = 'COMPLETED';
    statusFilter.dispatchEvent(new Event('change'));
    fixture.detectChanges();

    fixture.componentInstance.selectedIds.set([completedTraining.id]);
    fixture.componentInstance.openDeletionConfirmation();
    fixture.detectChanges();
    (fixture.nativeElement.querySelector('app-training-delete-confirmation .button--danger') as HTMLButtonElement).click();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(api.bulkDelete).toHaveBeenCalledWith([completedTraining.id]);
    expect(fixture.componentInstance.allTrainings().some((training) => training.id === completedTraining.id)).toBe(false);
    expect(visibleNames()).toEqual([]);
    expect(fixture.nativeElement.textContent).toContain('Zaznaczone treningi zostały trwale usunięte.');
  });

  it('keeps the training visible and allows retry after a delete API failure', async () => {
    let remaining = trainings;
    const deleteTraining = vi.fn()
      .mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 404 })))
      .mockImplementationOnce(() => { remaining = [trainings[0]]; return of(void 0); });
    await createPage(() => of(remaining), true);
    api.bulkDelete = deleteTraining;
    fixture.componentInstance.selectedIds.set([2]);
    fixture.componentInstance.openDeletionConfirmation();
    fixture.detectChanges();
    const confirmButton = fixture.nativeElement.querySelector(
      'app-training-delete-confirmation .button--danger',
    ) as HTMLButtonElement;
    confirmButton.click();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('app-training-delete-confirmation')).toBeTruthy();
    expect(fixture.nativeElement.textContent).toContain('Nie znaleziono treningu lub brak dostępu.');
    expect(visibleNames()).toHaveLength(2);

    confirmButton.click();
    fixture.detectChanges();
    expect(deleteTraining).toHaveBeenCalledTimes(2);
    expect(fixture.nativeElement.querySelector('app-training-delete-confirmation')).toBeNull();
    expect(visibleNames()).toEqual(['Trening wytrzymałościowy']);
  });

  it('prevents duplicate deletion submissions while the request is pending', async () => {
    const response = new Subject<void>();
    await createPage();
    api.bulkDelete.mockReturnValue(response.asObservable());
    fixture.componentInstance.selectedIds.set([2]);
    fixture.componentInstance.openDeletionConfirmation();
    fixture.detectChanges();
    const confirmButton = fixture.nativeElement.querySelector(
      'app-training-delete-confirmation .button--danger',
    ) as HTMLButtonElement;
    confirmButton.click();
    confirmButton.click();
    fixture.detectChanges();

    expect(api.bulkDelete).toHaveBeenCalledTimes(1);
    expect(confirmButton.disabled).toBe(true);
    expect(fixture.componentInstance.allTrainings()).toHaveLength(2);

    response.next();
    response.complete();
    fixture.detectChanges();
    expect(fixture.componentInstance.selectedIds()).toHaveLength(0);
  });

  it('searches by training name or group and filters by date range locally', async () => {
    await createPage();
    setSearch('  ŚREDNI  ');

    expect(visibleNames()).toEqual(['Trening wytrzymałościowy']);
    expect(api.getAll).toHaveBeenCalledTimes(1);

    setSearch('');
    const from = fixture.nativeElement.querySelector('[aria-label="Data treningu od"]') as HTMLInputElement;
    from.value = '2026-11-11';
    from.dispatchEvent(new Event('input'));
    const to = fixture.nativeElement.querySelector('[aria-label="Data treningu do"]') as HTMLInputElement;
    to.value = '2026-11-12';
    to.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    expect(visibleNames()).toEqual(['Sprint']);
    expect(api.getAll).toHaveBeenCalledTimes(1);
  });

  it('filters by multiple selected training groups', async () => {
    await createPage();

    const groupFilter = fixture.nativeElement.querySelector('app-multi-select-dropdown');
    (groupFilter.querySelector('button') as HTMLButtonElement).click();
    fixture.detectChanges();
    const checkbox = groupFilter.querySelector('input[type="checkbox"]') as HTMLInputElement;
    checkbox.checked = true;
    checkbox.dispatchEvent(new Event('change'));
    fixture.detectChanges();

    expect(visibleNames()).toEqual(['Sprint']);
    expect(groupFilter.textContent).toContain('Sprint');
    expect(api.getAll).toHaveBeenCalledTimes(1);
  });

  it('provides sort controls for the returned training data', async () => {
    await createPage();

    const dateSort = Array.from(fixture.nativeElement.querySelectorAll('thead th button')).find(
      (button) => (button as HTMLButtonElement).textContent?.includes('Data'),
    ) as HTMLButtonElement;
    dateSort.click();
    fixture.detectChanges();

    expect(visibleNames()).toEqual(['Trening wytrzymałościowy', 'Sprint']);
    expect(dateSort.closest('th')?.getAttribute('aria-sort')).toBe('ascending');
  });

  it('shows imports at the top even when training filters exclude all trainings', async () => {
    const review = {
      ...pendingImport,
      id: 22,
      status: 'REVIEW_REQUIRED' as const,
      name: 'Rozpoznany trening',
      trainingDate: '2026-11-11',
    };
    await createPage(() => of(trainings), true, () => of([review, pendingImport]));
    setSearch('nieznany');
    const rows = fixture.nativeElement.querySelectorAll('tbody tr') as NodeListOf<HTMLElement>;
    expect(rows).toHaveLength(2);
    expect(rows[0].textContent).toContain('Rozpoznany trening');
    expect(rows[0].textContent).toContain('Zweryfikuj import');
    expect(rows[0].querySelector('a')?.getAttribute('href')).toContain('/training-imports/22/review');
    expect(rows[1].textContent).toContain('Możliwy duplikat');
    expect(rows[1].textContent).toContain('Oczekuje na przetworzenie');
  });

  it('keeps import and training actions inside table cells with separate button containers', async () => {
    const review = { ...pendingImport, status: 'REVIEW_REQUIRED' as const };
    await createPage(() => of(trainings), true, () => of([review]));

    const cells = fixture.nativeElement.querySelectorAll('td.training-actions') as NodeListOf<HTMLTableCellElement>;
    expect(cells.length).toBeGreaterThan(1);
    for (const cell of cells) {
      expect(cell.querySelector(':scope > .training-action-buttons')).toBeTruthy();
    }
    expect(cells[0].querySelector('.training-action-buttons a')).toBeTruthy();
    expect(cells[1].querySelectorAll('.training-action-buttons .button').length).toBeGreaterThan(1);
  });

  it('refreshes only imports every five seconds until processing finishes', async () => {
    vi.useFakeTimers();
    try {
      let request = 0;
      await createPage(() => of(trainings), false, () => {
        request++;
        return of(request === 1 ? [pendingImport] : [{ ...pendingImport, status: 'REVIEW_REQUIRED' }]);
      });
      expect(importsApi.getActive).toHaveBeenCalledOnce();
      await vi.advanceTimersByTimeAsync(4999);
      expect(importsApi.getActive).toHaveBeenCalledOnce();
      await vi.advanceTimersByTimeAsync(1);
      fixture.detectChanges();
      expect(importsApi.getActive).toHaveBeenCalledTimes(2);
      expect(api.getAll).toHaveBeenCalledOnce();
      expect(fixture.nativeElement.textContent).toContain('Wymaga weryfikacji');
      await vi.advanceTimersByTimeAsync(10000);
      expect(importsApi.getActive).toHaveBeenCalledTimes(2);
    } finally {
      fixture.destroy();
      vi.useRealTimers();
    }
  });

  it('continues polling failed imports with an automatic retry and stops on destruction', async () => {
    vi.useFakeTimers();
    try {
      await createPage(() => of(trainings), false, () => of([{
        ...pendingImport,
        status: 'FAILED',
        nextRetryAt: '2026-11-12T10:00:30Z',
      }]));
      await vi.advanceTimersByTimeAsync(5000);
      expect(importsApi.getActive).toHaveBeenCalledTimes(2);
      fixture.destroy();
      await vi.advanceTimersByTimeAsync(10000);
      expect(importsApi.getActive).toHaveBeenCalledTimes(2);
    } finally {
      vi.useRealTimers();
    }
  });

  it('preserves rows after a refresh error and retries the failed fetch without overlapping', async () => {
    vi.useFakeTimers();
    try {
      const request = new Subject<TrainingImportListItem[]>();
      let call = 0;
      await createPage(() => of(trainings), false, () => {
        call++;
        return call === 1 ? of([pendingImport]) : request;
      });
      await vi.advanceTimersByTimeAsync(5000);
      fixture.componentInstance.refreshImports();
      expect(importsApi.getActive).toHaveBeenCalledTimes(2);
      request.error(new HttpErrorResponse({ status: 500 }));
      fixture.detectChanges();
      expect(fixture.nativeElement.textContent).toContain('Import nr 21');
      expect(fixture.nativeElement.textContent).toContain('Nie udało się pobrać importów treningów.');
      expect(importsApi.getActive).toHaveBeenCalledTimes(3);
    } finally {
      fixture.destroy();
      vi.useRealTimers();
    }
  });

  it('retries failed imports once while pending and refreshes the list on success', async () => {
    const failed = { ...pendingImport, status: 'FAILED' as const, failureMessage: 'Bezpieczny błąd' };
    const retry = new Subject<TrainingImportStatusResponse>();
    await createPage(() => of(trainings), true, () => of([failed]));
    importsApi.retry.mockReturnValue(retry);
    const button = fixture.nativeElement.querySelector('.import-row button') as HTMLButtonElement;
    expect(fixture.nativeElement.textContent).toContain('Bezpieczny błąd');
    button.click();
    button.click();
    fixture.detectChanges();
    expect(importsApi.retry).toHaveBeenCalledOnce();
    expect(importsApi.retry).toHaveBeenCalledWith(21);
    expect(button.disabled).toBe(true);
    retry.next({
      id: 21, trainingGroupId: 1, status: 'PROCESSING', nextRetryAt: null,
      createdAt: pendingImport.createdAt, possibleDuplicate: true, latestAttempt: null,
    });
    retry.complete();
    expect(importsApi.getActive).toHaveBeenCalledTimes(2);
  });

  it('keeps a failed import and displays an error if its retry fails', async () => {
    const failed = { ...pendingImport, status: 'FAILED' as const, failureMessage: 'Bezpieczny błąd' };
    await createPage(() => of(trainings), true, () => of([failed]));
    importsApi.retry.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
    (fixture.nativeElement.querySelector('.import-row button') as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Nie udało się ponowić przetwarzania.');
    expect(fixture.nativeElement.textContent).toContain('Bezpieczny błąd');
    expect((fixture.nativeElement.querySelector('.import-row button') as HTMLButtonElement).disabled).toBe(false);
    expect(importsApi.getActive).toHaveBeenCalledOnce();
  });

  it('keeps polling after retry succeeds even when the immediate refresh fails', async () => {
    vi.useFakeTimers();
    try {
      let request = 0;
      const failed = { ...pendingImport, status: 'FAILED' as const };
      await createPage(() => of(trainings), false, () => {
        request++;
        if (request === 2) return throwError(() => new HttpErrorResponse({ status: 500 }));
        return of(request === 1 ? [failed] : [{ ...pendingImport, status: 'PROCESSING' }]);
      });
      importsApi.retry.mockReturnValue(of({
        id: 21, trainingGroupId: 1, status: 'PROCESSING', nextRetryAt: null,
        createdAt: pendingImport.createdAt, possibleDuplicate: true, latestAttempt: null,
      }));
      fixture.componentInstance.retryImport(failed);
      fixture.detectChanges();
      expect(fixture.nativeElement.textContent).toContain('Przetwarzanie');
      expect(fixture.nativeElement.textContent).toContain('Nie udało się pobrać importów treningów.');
      await vi.advanceTimersByTimeAsync(5000);
      expect(importsApi.getActive).toHaveBeenCalledTimes(3);
    } finally {
      fixture.destroy();
      vi.useRealTimers();
    }
  });

  it('refreshes imports after upload without navigating away', async () => {
    await createPage();
    fixture.componentInstance.onTrainingImportUploaded({
      id: 21, trainingGroupId: 1, status: 'PENDING', possibleDuplicate: true,
    });
    fixture.detectChanges();
    expect(importsApi.getActive).toHaveBeenCalledTimes(2);
    expect(fixture.nativeElement.textContent).toContain('Możliwy duplikat');
  });
});

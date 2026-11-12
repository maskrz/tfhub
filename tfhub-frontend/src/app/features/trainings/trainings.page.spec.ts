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
  },
  {
    id: 2,
    name: 'Sprint',
    trainingDate: '2026-11-12',
    trainingGroup: { id: 1, name: 'Sprint' },
    definitionCount: 1,
    participantCount: 0,
    status: 'PLANNED',
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
};

const pendingImport: TrainingImportListItem = {
  id: 21,
  trainingGroupId: 1,
  trainingGroupName: 'Sprint',
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
    expect(fixture.nativeElement.querySelector('.delete-training-button')).toBeTruthy();

    statusFilter.value = 'PLANNED';
    statusFilter.dispatchEvent(new Event('change'));
    fixture.detectChanges();
    expect(visibleNames()).toHaveLength(2);

    statusFilter.value = '';
    statusFilter.dispatchEvent(new Event('change'));
    fixture.detectChanges();
    expect(visibleNames()).toHaveLength(3);
  });

  it('requires explicit confirmation and leaves a training unchanged when cancelled', async () => {
    await createPage(() => of([...trainings, completedTraining]));
    const statusFilter = fixture.nativeElement.querySelector('[aria-label="Filtruj według statusu"]') as HTMLSelectElement;
    statusFilter.value = 'COMPLETED';
    statusFilter.dispatchEvent(new Event('change'));
    fixture.detectChanges();
    const deleteButton = fixture.nativeElement.querySelector('[aria-label="Usuń trening: Trening techniczny"]') as HTMLButtonElement;
    deleteButton.click();
    fixture.detectChanges();

    const dialog = fixture.nativeElement.querySelector('app-training-delete-confirmation');
    expect(dialog.textContent).toContain('trwale usunąć');
    expect(dialog.textContent).toContain('wszystkie zapisane wyniki');
    expect(dialog.textContent).toContain('nie można cofnąć');
    (dialog.querySelector('.button--secondary') as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('app-training-delete-confirmation')).toBeNull();
    expect(api.deleteTraining).not.toHaveBeenCalled();
    expect(visibleNames()).toEqual(['Trening techniczny']);
  });

  it('deletes only after explicit confirmation and removes the training from the list', async () => {
    await createPage(() => of([...trainings, completedTraining]));
    const statusFilter = fixture.nativeElement.querySelector('[aria-label="Filtruj według statusu"]') as HTMLSelectElement;
    statusFilter.value = 'COMPLETED';
    statusFilter.dispatchEvent(new Event('change'));
    fixture.detectChanges();

    (fixture.nativeElement.querySelector('.delete-training-button') as HTMLButtonElement).click();
    fixture.detectChanges();
    (fixture.nativeElement.querySelector('app-training-delete-confirmation .button--danger') as HTMLButtonElement).click();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(api.deleteTraining).toHaveBeenCalledWith(completedTraining.id);
    expect(fixture.componentInstance.allTrainings().some((training) => training.id === completedTraining.id)).toBe(false);
    expect(visibleNames()).toEqual([]);
    expect(fixture.nativeElement.textContent).toContain('Trening został trwale usunięty.');
  });

  it('keeps the training visible and allows retry after a delete API failure', async () => {
    const deleteTraining = vi.fn()
      .mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 404 })))
      .mockReturnValueOnce(of(void 0));
    await createPage(() => of(trainings), true);
    api.deleteTraining = deleteTraining;
    (fixture.nativeElement.querySelector('[aria-label="Usuń trening: Sprint"]') as HTMLButtonElement).click();
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
    api.deleteTraining.mockReturnValue(response.asObservable());
    (fixture.nativeElement.querySelector('[aria-label="Usuń trening: Sprint"]') as HTMLButtonElement).click();
    fixture.detectChanges();
    const confirmButton = fixture.nativeElement.querySelector(
      'app-training-delete-confirmation .button--danger',
    ) as HTMLButtonElement;
    confirmButton.click();
    confirmButton.click();
    fixture.detectChanges();

    expect(api.deleteTraining).toHaveBeenCalledTimes(1);
    expect(confirmButton.disabled).toBe(true);
    expect(fixture.componentInstance.allTrainings()).toHaveLength(2);

    response.next();
    response.complete();
    fixture.detectChanges();
    expect(fixture.componentInstance.allTrainings()).toHaveLength(1);
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

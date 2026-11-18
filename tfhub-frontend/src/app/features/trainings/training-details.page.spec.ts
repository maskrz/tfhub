import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, provideRouter, Router } from '@angular/router';
import { signal } from '@angular/core';
import { Observable, Subject, of, throwError } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { TrainingDetails } from './training.model';
import { TrainingDetailsPage } from './training-details.page';
import { TrainingsApiService } from './trainings-api.service';

const training: TrainingDetails = {
  id: 12,
  name: 'Trening szybkościowy',
  trainingDate: '2026-11-11',
  status: 'PLANNED',
  trainingGroup: { id: 3, name: 'Sprint' },
  assignmentType: 'GROUP',
  assignedAthletes: [],
  definitions: [{
    id: 5,
    name: 'Główna',
    sequenceNumber: 1,
    segments: [
      { id: 8, sequenceNumber: 1, distanceM: 400, restAfterSeconds: 90 },
      { id: 9, sequenceNumber: 2, distanceM: 200, restAfterSeconds: null },
    ],
    participations: [{
      id: 11,
      athleteId: 21,
      firstName: 'Anna',
      lastName: 'Nowak',
      executions: [
        { id: 17, segmentId: 8, segmentSequenceNumber: 1, status: 'NOT_COMPLETED', measuredResult: null, usesSpikes: false },
        { id: 18, segmentId: 9, segmentSequenceNumber: 2, status: 'PENDING', measuredResult: null, usesSpikes: false },
      ],
    }, {
      id: 10,
      athleteId: 20,
      firstName: 'Jan',
      lastName: 'Kowalski',
      executions: [
        { id: 15, segmentId: 8, segmentSequenceNumber: 1, status: 'RECORDED', measuredResult: 52.4, usesSpikes: true },
        { id: 16, segmentId: 9, segmentSequenceNumber: 2, status: 'COMPLETED_WITHOUT_RESULT', measuredResult: null, usesSpikes: false },
      ],
    }],
  }, {
    id: 6,
    name: 'Rozgrzewka',
    sequenceNumber: 2,
    segments: [{ id: 18, sequenceNumber: 1, distanceM: 100, restAfterSeconds: null }],
    participations: [],
  }],
  trainingType: 'TEMPO',
  sections: [],
};

describe('TrainingDetailsPage', () => {
  let fixture: ComponentFixture<TrainingDetailsPage>;
  let api: {
    getById: ReturnType<typeof vi.fn>;
    deleteTraining: ReturnType<typeof vi.fn>;
    completeFreeText: ReturnType<typeof vi.fn>;
  };

  afterEach(() => vi.unstubAllGlobals());

  function mockClipboard(writeText?: (text: string) => Promise<void>): void {
    const mockNavigator = Object.create(navigator) as Navigator;
    Object.defineProperty(mockNavigator, 'clipboard', {
      value: writeText ? { writeText } : undefined,
      configurable: true,
    });
    vi.stubGlobal('navigator', mockNavigator);
  }

  async function createPage(
    loader: () => Observable<TrainingDetails> = () => of(training),
    id = '12',
    role: 'ADMIN' | 'COACH' | 'ATHLETE' = 'ADMIN',
  ): Promise<void> {
    api = {
      getById: vi.fn(loader),
      deleteTraining: vi.fn(() => of(void 0)),
      completeFreeText: vi.fn(() => of({ id: 12 })),
    };
    await TestBed.configureTestingModule({
      imports: [TrainingDetailsPage],
      providers: [
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => id } } } },
        { provide: TrainingsApiService, useValue: api },
        { provide: AuthService, useValue: { currentUser: signal({
          authenticated: true, userId: 1, username: role.toLowerCase(), role,
          athleteId: role === 'ATHLETE' ? 1 : null,
        }) } },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(TrainingDetailsPage);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  it('displays training data, ordered plan, historical participants, executions, workflow links and delete action', async () => {
    await createPage();
    const text = fixture.nativeElement.textContent as string;
    const definitionHeadings = [...fixture.nativeElement.querySelectorAll('.definition-card h2')]
      .map((heading: HTMLElement) => heading.textContent?.trim());
    const segmentHeadings = [...fixture.nativeElement.querySelectorAll('.segments-table tbody th')]
      .map((heading: HTMLElement) => heading.textContent?.trim());
    const executionRows = [...fixture.nativeElement.querySelectorAll('.executions-table tbody tr')];
    const annaCells = [...executionRows[0].querySelectorAll('td')];
    const janCells = [...executionRows[1].querySelectorAll('td')];

    expect(api.getById).toHaveBeenCalledWith(12);
    expect(text).toContain('Trening szybkościowy');
    expect(text).toContain('11.11.2026');
    expect(text).toContain('Sprint');
    expect(text).toContain('Planowany');
    expect(definitionHeadings).toEqual(['1. Główna', '2. Rozgrzewka']);
    expect(segmentHeadings).toEqual(['1', '2', '1']);
    const segmentRegions = fixture.nativeElement.querySelectorAll('.segments-surface[role="region"][tabindex="0"]') as NodeListOf<HTMLElement>;
    expect([...segmentRegions].map((region) => region.getAttribute('aria-label'))).toEqual(['Odcinki: Główna', 'Odcinki: Rozgrzewka']);
    const restCells = [...fixture.nativeElement.querySelectorAll('.segments-table tbody tr')]
      .map((row: HTMLElement) => row.querySelectorAll('td')[1].textContent?.trim());
    expect(restCells).toEqual(['1m 30s', '—', '—']);
    expect(text).toContain('Jan Kowalski');
    expect(text).toContain('Anna Nowak');
    expect(annaCells.map((cell: HTMLElement) => cell.textContent.trim())).toEqual(['-', '']);
    expect(janCells.map((cell: HTMLElement) => cell.textContent.trim())).toEqual(['52,40Kolce', 'X']);
    expect(fixture.nativeElement.querySelector('.execution-status')).toBeNull();
    expect(fixture.nativeElement.querySelectorAll('.spikes-label')).toHaveLength(1);
    expect(fixture.nativeElement.querySelector('a[href="/trainings/12/edit"]')?.textContent.trim()).toBe('Edytuj trening');
    expect(fixture.nativeElement.querySelector('a[href="/trainings/12/complete"]')?.textContent.trim()).toBe('Uzupełnij trening');
    expect(fixture.nativeElement.querySelector('.delete-training-button')).toBeTruthy();
    expect(fixture.nativeElement.querySelectorAll('.training-actions')).toHaveLength(1);
    expect(fixture.nativeElement.querySelectorAll('.training-actions > .button')).toHaveLength(3);
  });

  it('shows completed trainings with edit and completion actions', async () => {
    await createPage(() => of({ ...training, status: 'COMPLETED' }));
    const text = fixture.nativeElement.textContent as string;

    expect(text).toContain('Zakończony');
    expect(fixture.nativeElement.querySelector('a[href="/trainings/12/edit"]')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('a[href="/trainings/12/complete"]')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('.delete-training-button')).toBeTruthy();
    expect(text).toContain('Jan Kowalski');
  });

  it('displays FREE_TEXT sections, uses the matching edit flow, and supports completion', async () => {
    const freeText: TrainingDetails = {
      ...training,
      trainingType: 'FREE_TEXT',
      equipment: 'Piłka\nGumy',
      definitions: [],
      sections: [
        { id: 31, title: 'Rozgrzewka', content: 'Trucht 10 minut', sequenceNumber: 1 },
        { id: 32, title: 'Część główna', content: 'Skip A', sequenceNumber: 2 },
      ],
    };
    await createPage(() => of(freeText));

    expect(fixture.nativeElement.textContent).toContain('1. Rozgrzewka');
    expect(fixture.nativeElement.textContent).toContain('Trucht 10 minut');
    expect(fixture.nativeElement.textContent).toContain('2. Część główna');
    expect(fixture.nativeElement.querySelector('#equipment-heading')?.parentElement.textContent).toContain('Piłka\nGumy');
    expect(fixture.nativeElement.querySelector('a[href="/trainings/12/free-text/edit"]')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('a[href="/trainings/12/complete"]')).toBeNull();
    expect(fixture.nativeElement.textContent).toContain('Oznacz jako zakończony');

    fixture.componentInstance.completeFreeTextTraining();
    await fixture.whenStable();
    expect(api.completeFreeText).toHaveBeenCalledWith(12);
  });

  it('shows assigned FREE_TEXT details to athletes without management actions', async () => {
    const individual: TrainingDetails = {
      ...training,
      trainingType: 'FREE_TEXT',
      equipment: 'Prywatny sprzęt',
      assignmentType: 'ATHLETES',
      trainingGroup: null,
      definitions: [],
      sections: [{ id: 31, title: 'Część główna', content: 'Ćwiczenia biegowe', sequenceNumber: 1 }],
    };
    await createPage(() => of(individual), '12', 'ATHLETE');

    expect(fixture.nativeElement.textContent).toContain('Trening przypisany bezpośrednio do Ciebie.');
    expect(fixture.nativeElement.textContent).toContain('Ćwiczenia biegowe');
    expect(fixture.nativeElement.textContent).not.toContain('Prywatny sprzęt');
    expect(fixture.nativeElement.querySelector('#equipment-heading')).toBeNull();
    expect(fixture.nativeElement.querySelector('.training-actions')).toBeNull();
    expect(fixture.nativeElement.querySelector('.delete-training-button')).toBeNull();
  });

  it('displays rest in minutes, seconds and an empty marker', async () => {
    await createPage(() => of({
      ...training,
      definitions: [{
        ...training.definitions[0],
        segments: [240, 250, 45, 0, null].map((restAfterSeconds, index) => ({
          id: index + 1, sequenceNumber: index + 1, distanceM: 200, restAfterSeconds,
        })),
      }],
    }));
    const restCells = [...fixture.nativeElement.querySelectorAll('.segments-table tbody tr')]
      .map((row: HTMLElement) => row.querySelectorAll('td')[1].textContent?.trim());
    expect(restCells).toEqual(['4m', '4m 10s', '45s', '0m', '—']);
  });

  it('requires confirmation before deleting from details and returns to the list on success', async () => {
    await createPage();
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    (fixture.nativeElement.querySelector('.delete-training-button') as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('trwale usunąć');
    expect(api.deleteTraining).not.toHaveBeenCalled();
    (fixture.nativeElement.querySelector('app-training-delete-confirmation .button--danger') as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(api.deleteTraining).toHaveBeenCalledWith(12);
    expect(navigate).toHaveBeenCalledWith(['/trainings']);
  });

  it('displays empty definitions and participant collections without errors', async () => {
    await createPage(() => of({ ...training, definitions: [] }));
    expect(fixture.nativeElement.textContent).toContain('Ten trening nie ma jeszcze podgrup.');
  });

  it('displays empty participant collections without errors', async () => {
    await createPage(() => of({
      ...training,
      definitions: [{ ...training.definitions[0], participations: [] }],
    }));
    expect(fixture.nativeElement.textContent).toContain('Nie przypisano zawodników do tej podgrupy.');
  });

  it('displays blank result cells and no spikes label for pending or missing execution data', async () => {
    const pendingTraining: TrainingDetails = {
      ...training,
      definitions: [{
        ...training.definitions[0],
        participations: [{
          ...training.definitions[0].participations[0],
          executions: [{
            ...training.definitions[0].participations[0].executions[1],
            status: 'PENDING' as const,
            measuredResult: null,
            usesSpikes: false,
          }],
        }],
      }],
      trainingType: 'TEMPO',
    };
    await createPage(() => of(pendingTraining));
    const resultCells = [...fixture.nativeElement.querySelectorAll('.executions-table tbody td')] as HTMLElement[];

    expect(resultCells.map((cell) => cell.textContent.trim())).toEqual(['', '']);
    expect(fixture.nativeElement.querySelectorAll('.spikes-label')).toHaveLength(0);
  });

  it('shows loading then error state and can retry', async () => {
    const pending = new Subject<TrainingDetails>();
    api = {
      getById: vi.fn(() => pending.asObservable()),
      deleteTraining: vi.fn(() => of(void 0)),
      completeFreeText: vi.fn(() => of({ id: 12 })),
    };
    await TestBed.configureTestingModule({
      imports: [TrainingDetailsPage],
      providers: [
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => '12' } } } },
        { provide: TrainingsApiService, useValue: api },
        { provide: AuthService, useValue: { currentUser: signal({
          authenticated: true, userId: 1, username: 'admin', role: 'ADMIN', athleteId: null,
        }) } },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(TrainingDetailsPage);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Ładowanie treningu...');
    pending.error(new HttpErrorResponse({ status: 404 }));
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Nie znaleziono treningu.');

    api.getById.mockReturnValue(of(training));
    fixture.nativeElement.querySelector('button').click();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Trening szybkościowy');
    expect(api.getById).toHaveBeenCalledTimes(2);
  });

  it('shows a generic message for unexpected load failures', async () => {
    await createPage(() => throwError(() => new Error('unexpected')));

    expect(fixture.nativeElement.textContent).toContain('Nie udało się pobrać danych treningu.');
  });

  it('rejects invalid training identifiers without making an API request', async () => {
    await createPage(() => of(training), 'invalid');

    expect(fixture.nativeElement.textContent).toContain('Nieprawidłowy identyfikator treningu.');
    expect(api.getById).not.toHaveBeenCalled();
  });

  it.each(['ADMIN', 'COACH'] as const)('copies only the selected TEMPO subgroup for %s', async (role) => {
    const writeText = vi.fn().mockResolvedValue(undefined);
    mockClipboard(writeText);
    await createPage(() => of({ ...training, status: 'COMPLETED' }), '12', role);
    const buttons = fixture.nativeElement.querySelectorAll('.copy-definition-button') as NodeListOf<HTMLButtonElement>;
    expect(buttons).toHaveLength(2);
    expect(buttons[1].getAttribute('aria-label')).toBe('Kopiuj podgrupę: Rozgrzewka');
    expect(buttons[1].querySelector('svg[aria-hidden="true"]')).toBeTruthy();
    expect(buttons[1].textContent?.trim()).toBe('');
    expect(buttons[1].closest('.section-heading')).toBeTruthy();
    buttons[1].click();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(writeText).toHaveBeenCalledExactlyOnceWith(
      'Trening szybkościowy\n11.11.2026\n\nRozgrzewka\n\n1. 100 m — przerwa: —',
    );
    const cards = fixture.nativeElement.querySelectorAll('.definition-card');
    expect(cards[0].querySelector('.copy-feedback')).toBeNull();
    expect(cards[1].querySelector('.copy-feedback[role="status"]')?.textContent).toContain('Skopiowano');
  });

  it.each(['ADMIN', 'COACH'] as const)('copies a FREE_TEXT training without equipment or assignments for %s', async (role) => {
    const writeText = vi.fn().mockResolvedValue(undefined);
    mockClipboard(writeText);
    await createPage(() => of({
      ...training, trainingType: 'FREE_TEXT', equipment: 'Tajny sprzęt',
      assignedAthletes: [{ id: 100, firstName: 'Jan', lastName: 'Kowalski' }],
      sections: [{ id: 31, title: 'Rozgrzewka', content: 'Trucht\nSkip A', sequenceNumber: 1 }],
    }), '12', role);
    const button = fixture.nativeElement.querySelector('.copy-training-button') as HTMLButtonElement;
    expect(button.getAttribute('aria-label')).toBe('Kopiuj trening');
    expect(button.querySelector('svg[aria-hidden="true"]')).toBeTruthy();
    expect(button.textContent?.trim()).toBe('');
    expect(button.closest('.summary-heading')).toBeTruthy();
    expect(button.closest('.training-actions')).toBeNull();
    button.click();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(writeText).toHaveBeenCalledExactlyOnceWith('Trening szybkościowy\n11.11.2026\n\nRozgrzewka\nTrucht\nSkip A');
    expect(fixture.nativeElement.querySelector('.training-summary .copy-feedback[role="status"]')?.textContent)
      .toBe('Skopiowano do schowka');
  });

  it('does not expose copy actions to athletes', async () => {
    await createPage(() => of({ ...training, trainingType: 'FREE_TEXT', sections: [] }), '12', 'ATHLETE');
    expect(fixture.nativeElement.querySelector('.copy-training-button')).toBeNull();
    expect(fixture.nativeElement.querySelector('.copy-definition-button')).toBeNull();
    mockClipboard(vi.fn().mockResolvedValue(undefined));
    fixture.componentInstance.copyFreeTextTraining();
    expect(navigator.clipboard.writeText).not.toHaveBeenCalled();
  });

  it('copies empty undated FREE_TEXT and TEMPO definitions', async () => {
    const writeText = vi.fn().mockResolvedValue(undefined);
    mockClipboard(writeText);
    await createPage(() => of({ ...training, trainingType: 'FREE_TEXT', trainingDate: null, sections: [] }));
    (fixture.nativeElement.querySelector('.copy-training-button') as HTMLButtonElement).click();
    await fixture.whenStable();
    expect(writeText).toHaveBeenCalledWith('Trening szybkościowy');

    fixture.componentInstance.training.set({
      ...training, trainingDate: null, definitions: [{ ...training.definitions[0], segments: [] }],
    });
    fixture.detectChanges();
    (fixture.nativeElement.querySelector('.copy-definition-button') as HTMLButtonElement).click();
    await fixture.whenStable();
    expect(writeText).toHaveBeenLastCalledWith('Trening szybkościowy\n\nGłówna');
  });

  it.each(['rejected', 'unavailable'] as const)('reports clipboard %s and clears old feedback on retry', async (failure) => {
    const writeText = vi.fn().mockRejectedValue(new Error('permission denied'));
    mockClipboard(failure === 'rejected' ? writeText : undefined);
    await createPage();
    const button = fixture.nativeElement.querySelector('.copy-definition-button') as HTMLButtonElement;
    button.click();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.copy-feedback[role="alert"]')?.textContent).toContain('Nie udało się');

    const successfulWrite = vi.fn().mockResolvedValue(undefined);
    mockClipboard(successfulWrite);
    button.click();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.copy-feedback[role="alert"]')).toBeNull();
    expect(fixture.nativeElement.querySelector('.copy-feedback[role="status"]')?.textContent).toContain('Skopiowano');
  });

  it('prevents overlapping copy operations until the clipboard write finishes', async () => {
    let finishWrite!: () => void;
    const writeText = vi.fn(() => new Promise<void>((resolve) => { finishWrite = resolve; }));
    mockClipboard(writeText);
    await createPage();
    const buttons = fixture.nativeElement.querySelectorAll('.copy-definition-button') as NodeListOf<HTMLButtonElement>;
    buttons[0].click();
    fixture.detectChanges();
    expect(buttons[1].disabled).toBe(true);
    fixture.componentInstance.copyTempoDefinition(training.definitions[1]);
    expect(writeText).toHaveBeenCalledTimes(1);
    finishWrite();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(buttons[1].disabled).toBe(false);
  });

  it('dismisses the success bubble after a short delay', async () => {
    mockClipboard(vi.fn().mockResolvedValue(undefined));
    await createPage();
    vi.useFakeTimers();
    try {
      (fixture.nativeElement.querySelector('.copy-definition-button') as HTMLButtonElement).click();
      await Promise.resolve();
      fixture.detectChanges();
      expect(fixture.nativeElement.querySelector('.copy-control .copy-feedback[role="status"]')?.textContent)
        .toBe('Skopiowano do schowka');
      vi.advanceTimersByTime(3000);
      fixture.detectChanges();
      expect(fixture.nativeElement.querySelector('.copy-feedback')).toBeNull();
    } finally {
      vi.useRealTimers();
    }
  });
});

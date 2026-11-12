import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, provideRouter, Router } from '@angular/router';
import { Observable, Subject, of, throwError } from 'rxjs';
import { TrainingDetails } from './training.model';
import { TrainingDetailsPage } from './training-details.page';
import { TrainingsApiService } from './trainings-api.service';

const training: TrainingDetails = {
  id: 12,
  name: 'Trening szybkościowy',
  trainingDate: '2026-11-11',
  status: 'PLANNED',
  trainingGroup: { id: 3, name: 'Sprint' },
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
};

describe('TrainingDetailsPage', () => {
  let fixture: ComponentFixture<TrainingDetailsPage>;
  let api: {
    getById: ReturnType<typeof vi.fn>;
    deleteTraining: ReturnType<typeof vi.fn>;
  };

  async function createPage(
    loader: () => Observable<TrainingDetails> = () => of(training),
    id = '12',
  ): Promise<void> {
    api = {
      getById: vi.fn(loader),
      deleteTraining: vi.fn(() => of(void 0)),
    };
    await TestBed.configureTestingModule({
      imports: [TrainingDetailsPage],
      providers: [
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => id } } } },
        { provide: TrainingsApiService, useValue: api },
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
    };
    await TestBed.configureTestingModule({
      imports: [TrainingDetailsPage],
      providers: [
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => '12' } } } },
        { provide: TrainingsApiService, useValue: api },
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
});

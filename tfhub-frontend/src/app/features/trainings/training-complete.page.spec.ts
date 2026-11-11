import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, provideRouter } from '@angular/router';
import { Observable, of, throwError } from 'rxjs';
import { AthleteResponse } from '../athletes/athlete.model';
import { AthletesApiService } from '../athletes/athletes-api.service';
import { CompleteTrainingRequest, TrainingDetails, UpdateTrainingResponse } from './training.model';
import { TrainingCompletePage } from './training-complete.page';
import { TrainingsApiService } from './trainings-api.service';

const training: TrainingDetails = {
  id: 12,
  name: 'Trening szybkościowy',
  trainingDate: '2026-11-11',
  active: true,
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
      id: 10,
      athleteId: 20,
      firstName: 'Jan',
      lastName: 'Kowalski',
      executions: [
        { id: 15, segmentId: 8, segmentSequenceNumber: 1, status: 'RECORDED', measuredResult: 52.4, usesSpikes: true },
        { id: 16, segmentId: 9, segmentSequenceNumber: 2, status: 'PENDING', measuredResult: null, usesSpikes: false },
      ],
    }],
  }, {
    id: 6,
    name: 'Alternatywna',
    sequenceNumber: 2,
    segments: [{ id: 18, sequenceNumber: 1, distanceM: 100, restAfterSeconds: null }],
    participations: [],
  }],
};

const athletes: AthleteResponse[] = [
  { id: 20, firstName: 'Jan', lastName: 'Kowalski', birthYear: 2010, gender: 'MALE', active: true, trainingGroupId: 2 },
  { id: 21, firstName: 'Anna', lastName: 'Nowak', birthYear: 2011, gender: 'FEMALE', active: true, trainingGroupId: 3 },
  { id: 22, firstName: 'Piotr', lastName: 'Wiśniewski', birthYear: 2009, gender: 'MALE', active: true, trainingGroupId: 4 },
];

describe('TrainingCompletePage', () => {
  let fixture: ComponentFixture<TrainingCompletePage>;
  let trainingsApi: {
    getById: ReturnType<typeof vi.fn>;
    complete: ReturnType<typeof vi.fn>;
  };

  async function createPage(
    completeLoader: (request: CompleteTrainingRequest) => Observable<UpdateTrainingResponse> = () => of({ id: 12 }),
    detail: TrainingDetails = training,
  ): Promise<void> {
    trainingsApi = {
      getById: vi.fn(() => of(detail)),
      complete: vi.fn((_id: number, request: CompleteTrainingRequest) => completeLoader(request)),
    };
    await TestBed.configureTestingModule({
      imports: [TrainingCompletePage],
      providers: [
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => '12' } } },
        },
        { provide: TrainingsApiService, useValue: trainingsApi },
        { provide: AthletesApiService, useValue: { getAll: vi.fn(() => of(athletes)) } },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(TrainingCompletePage);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  it('loads historical assignments even when an athlete has changed groups', async () => {
    await createPage();
    const page = fixture.componentInstance;

    expect(page.selectedIds(0)).toEqual([20]);
    expect(page.optionsFor(0)).toContainEqual({ id: 20, name: 'Jan Kowalski' });
    expect(page.optionsFor(0)).toContainEqual({ id: 21, name: 'Anna Nowak' });
    expect(page.optionsFor(0).map((option) => option.id)).not.toContain(22);
    expect(page.executionFor(page.participants(0).at(0), 8)?.controls.id.value).toBe(15);
    expect(page.executionFor(page.participants(0).at(0), 8)?.controls.measuredResult.value).toBe('52,40');
    expect(fixture.nativeElement.querySelector('.results-table')).not.toBeNull();
    expect(fixture.nativeElement.textContent).toContain('Odcinek 1');
    expect(fixture.nativeElement.querySelector('.results-table thead').textContent).toContain('400m / 1min 30s');
    expect(fixture.nativeElement.querySelector('.results-table thead').textContent).toContain('200m');
    expect(fixture.nativeElement.querySelector('.results-table thead').textContent).not.toContain('200m /');
    expect(fixture.nativeElement.querySelectorAll('.results-table tfoot th')).toHaveLength(3);
    expect(fixture.nativeElement.querySelector('.results-table tfoot').textContent).toContain('Odcinek 2');
    expect(fixture.nativeElement.querySelector('.results-table tfoot').textContent).toContain('400m / 1min 30s');
    expect(fixture.nativeElement.querySelector('.spikes-control span')?.textContent).toBe('K');
    expect(fixture.nativeElement.querySelector('footer a').getAttribute('href')).toBe('/trainings');
  });

  it('prevents assigning one athlete to multiple definitions', async () => {
    await createPage();
    const page = fixture.componentInstance;

    page.onAthletesChange(0, [20, 21]);

    expect(page.optionsFor(1).map((option) => option.id)).toEqual([]);
    page.onAthletesChange(1, [21]);
    expect(page.selectedIds(1)).toEqual([]);
  });

  it('submits explicit additions and existing execution updates without removing omitted participants', async () => {
    await createPage();
    const page = fixture.componentInstance;
    page.onAthletesChange(0, [20, 21]);
    const existing = page.participants(0).at(0);
    const result = page.executionFor(existing, 9)!;
    result.controls.status.setValue('RECORDED');
    result.controls.measuredResult.setValue('00:00,50');
    page.submit();
    await fixture.whenStable();

    const request = trainingsApi.complete.mock.calls[0][1] as CompleteTrainingRequest;
    expect(request.add).toHaveLength(1);
    expect(request.add[0].athleteId).toBe(21);
    expect(request.add[0].executionUpdates).toEqual([]);
    expect(request.update[0]).toEqual({
      id: 10,
      executionUpdates: [
        { id: 16, status: 'RECORDED', measuredResult: 0.5, usesSpikes: false },
      ],
    });
    expect(request.removeParticipationIds).toEqual([]);
    expect(trainingsApi.getById).toHaveBeenCalledTimes(2);
    expect(page.training()?.id).toBe(12);
    expect(page.successMessage()).toBe('Uzupełnienie treningu zostało zapisane.');
  });

  it('maps X and - to their execution statuses and submits spikes per segment', async () => {
    await createPage();
    const page = fixture.componentInstance;
    const participant = page.participants(0).at(0);
    const completedWithoutResult = page.executionFor(participant, 9)!;
    completedWithoutResult.controls.measuredResult.setValue('x');
    completedWithoutResult.controls.usesSpikes.setValue(true);
    page.resultChanged(completedWithoutResult);
    expect(completedWithoutResult.controls.measuredResult.value).toBe('x');
    page.formatResultOnBlur(completedWithoutResult);
    expect(completedWithoutResult.controls.measuredResult.value).toBe('X');
    expect(completedWithoutResult.controls.status.value).toBe('COMPLETED_WITHOUT_RESULT');

    const notCompleted = page.executionFor(participant, 8)!;
    notCompleted.controls.measuredResult.setValue('-');
    page.resultChanged(notCompleted);
    expect(notCompleted.controls.status.value).toBe('NOT_COMPLETED');

    page.submit();

    const request = trainingsApi.complete.mock.calls[0][1] as CompleteTrainingRequest;
    expect(request.update[0].executionUpdates).toEqual([
      { id: 15, status: 'NOT_COMPLETED', measuredResult: null, usesSpikes: true },
      { id: 16, status: 'COMPLETED_WITHOUT_RESULT', measuredResult: null, usesSpikes: true },
    ]);
  });

  it('displays saved non-result statuses as X and -', async () => {
    const savedStatuses: TrainingDetails = {
      ...training,
      definitions: training.definitions.map((definition) => ({
        ...definition,
        participations: definition.participations.map((participant) => ({
          ...participant,
          executions: participant.executions.map((execution) => ({
            ...execution,
            status: execution.segmentId === 8 ? 'NOT_COMPLETED' as const : 'COMPLETED_WITHOUT_RESULT' as const,
            measuredResult: null,
          })),
        })),
      })),
    };
    await createPage(() => of({ id: 12 }), savedStatuses);
    const participant = fixture.componentInstance.participants(0).at(0);

    expect(fixture.componentInstance.executionFor(participant, 8)?.controls.measuredResult.value).toBe('-');
    expect(fixture.componentInstance.executionFor(participant, 9)?.controls.measuredResult.value).toBe('X');
  });

  it('formats accepted time variations only after focus is lost', async () => {
    await createPage();
    const page = fixture.componentInstance;
    const execution = page.executionFor(page.participants(0).at(0), 9)!;
    execution.controls.measuredResult.setValue('1,11,1');
    page.resultChanged(execution);
    expect(execution.controls.measuredResult.value).toBe('1,11,1');
    page.formatResultOnBlur(execution);
    expect(execution.controls.measuredResult.value).toBe('1:11,10');
    expect(execution.controls.status.value).toBe('RECORDED');

    const formatCases: [string, string][] = [
      ['1.11.1', '1:11,10'],
      ['1;11', '1:11'],
      ['1:11', '1:11'],
      ['47', '47'],
      ['41,1', '41,10'],
      ['44.1', '44,1'],
    ];
    for (const [input, expected] of formatCases) {
      execution.controls.measuredResult.setValue(input);
      page.resultChanged(execution);
      page.formatResultOnBlur(execution);
      expect(execution.controls.measuredResult.value).toBe(expected);
    }
  });

  it('marks invalid results with a red border and does not show a validation message', async () => {
    await createPage();
    const page = fixture.componentInstance;
    const execution = page.executionFor(page.participants(0).at(0), 9)!;
    execution.controls.measuredResult.setValue('1:72');
    page.resultChanged(execution);
    page.submit();
    fixture.detectChanges();

    const input = fixture.nativeElement.querySelector(
      `[id="${page.resultInputId(0, 20, 9)}"]`,
    ) as HTMLInputElement;
    expect(page.resultInvalid(execution)).toBe(true);
    expect(input.getAttribute('aria-invalid')).toBe('true');
    expect(fixture.nativeElement.querySelector('.result-error')).toBeNull();
    expect(trainingsApi.complete).not.toHaveBeenCalled();
  });

  it('prevents removing participants with recorded executions', async () => {
    await createPage();
    const page = fixture.componentInstance;

    page.onAthletesChange(0, []);

    expect(page.assignmentMessage()).toContain('zapisanym wynikiem');
    expect(page.selectedIds(0)).toEqual([20]);
  });

  it('sends an explicit removal for an eligible participant', async () => {
    const noRecordedTraining: TrainingDetails = {
      ...training,
      definitions: training.definitions.map((definition) => ({
        ...definition,
        participations: definition.participations.map((participant) => ({
          ...participant,
          executions: participant.executions.map((execution) => ({
            ...execution,
            status: 'PENDING' as const,
            measuredResult: null,
            usesSpikes: false,
          })),
        })),
      })),
    };
    await createPage(() => of({ id: 12 }), noRecordedTraining);
    const page = fixture.componentInstance;
    page.onAthletesChange(0, []);
    page.submit();

    expect(trainingsApi.complete).toHaveBeenCalledWith(12, {
      add: [],
      update: [],
      removeParticipationIds: [10],
    });
  });

  it('keeps the completion page open and auto-dismisses the success notice after five seconds', async () => {
    await createPage();
    const page = fixture.componentInstance;
    vi.useFakeTimers();

    try {
      page.submit();
      fixture.detectChanges();

      expect(page.successMessage()).toBe('Uzupełnienie treningu zostało zapisane.');
      expect(fixture.nativeElement.querySelector('.success-toast')).not.toBeNull();
      expect(fixture.nativeElement.querySelector('[aria-label="Zamknij powiadomienie"]')).not.toBeNull();

      vi.advanceTimersByTime(4999);
      expect(page.successMessage()).not.toBeNull();
      vi.advanceTimersByTime(1);
      fixture.detectChanges();
      expect(page.successMessage()).toBeNull();
      expect(fixture.nativeElement.querySelector('.success-toast')).toBeNull();
    } finally {
      vi.useRealTimers();
    }
  });

  it('allows dismissing the success notice early', async () => {
    await createPage();
    const page = fixture.componentInstance;
    page.submit();
    fixture.detectChanges();

    fixture.nativeElement.querySelector('[aria-label="Zamknij powiadomienie"]').click();
    fixture.detectChanges();

    expect(page.successMessage()).toBeNull();
    expect(fixture.nativeElement.querySelector('.success-toast')).toBeNull();
  });

  it('validates time format and keeps changes after failed submission', async () => {
    await createPage(() => throwError(() => new HttpErrorResponse({
      status: 400,
      error: { detail: 'Execution result must be positive' },
    })));
    const page = fixture.componentInstance;
    const pending = page.executionFor(page.participants(0).at(0), 9)!;
    pending.controls.measuredResult.setValue('00:00:12');
    page.resultChanged(pending);
    page.submit();
    expect(page.resultInvalid(pending)).toBe(true);
    expect(trainingsApi.complete).not.toHaveBeenCalled();

    pending.controls.measuredResult.setValue('00:14');
    page.resultChanged(pending);
    page.formatResultOnBlur(pending);
    page.submit();
    await fixture.whenStable();

    expect(page.executionFor(page.participants(0).at(0), 9)?.controls.measuredResult.value).toBe('00:14');
    expect(page.errorMessage()).toContain('Nie udało się zapisać');
  });

  it('does not allow completion of inactive trainings', async () => {
    await createPage(() => of({ id: 12 }), { ...training, active: false });

    expect(fixture.componentInstance.loadError()).toContain('Nie można uzupełnić nieaktywnego treningu');
    expect(fixture.componentInstance.definitions.length).toBe(0);
  });
});

import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, provideRouter, Router } from '@angular/router';
import { Observable, of, throwError } from 'rxjs';
import { TrainingEditPage } from './training-edit.page';
import { EditTrainingRequest, TrainingDetails, UpdateTrainingResponse } from './training.model';
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
    participations: [],
  }],
};

describe('TrainingEditPage', () => {
  let fixture: ComponentFixture<TrainingEditPage>;
  let trainingsApi: {
    getById: ReturnType<typeof vi.fn>;
    update: ReturnType<typeof vi.fn>;
  };

  async function createPage(
    updateLoader: (request: EditTrainingRequest) => Observable<UpdateTrainingResponse> = () => of({ id: 12 }),
    detail: TrainingDetails = training,
  ): Promise<void> {
    trainingsApi = {
      getById: vi.fn(() => of(detail)),
      update: vi.fn((_id: number, request: EditTrainingRequest) => updateLoader(request)),
    };
    await TestBed.configureTestingModule({
      imports: [TrainingEditPage],
      providers: [
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => '12' } } } },
        { provide: TrainingsApiService, useValue: trainingsApi },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(TrainingEditPage);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  it('loads the training and displays its immutable group', async () => {
    await createPage();
    const page = fixture.componentInstance;

    expect(trainingsApi.getById).toHaveBeenCalledWith(12);
    expect(page.form.controls.name.value).toBe('Trening szybkościowy');
    expect(page.form.controls.trainingDate.value).toBe('2026-11-11');
    expect(fixture.nativeElement.querySelector('#training-group').value).toBe('Sprint');
    expect(fixture.nativeElement.querySelector('#training-group').readOnly).toBe(true);
    expect(page.definitions.at(0).controls.segments.at(0).controls.id.value).toBe(8);
    expect(page.definitions.at(0).controls.segments.at(0).controls.restAfterSeconds.value).toBe('90s');
  });

  it('reorders definitions and segments while preserving their IDs', async () => {
    await createPage();
    const page = fixture.componentInstance;
    page.addDefinition();
    page.definitions.at(1).controls.name.setValue('Rozgrzewka');
    page.definitions.at(1).controls.segments.at(0).controls.distanceM.setValue(100);
    page.moveDefinition(1, -1);
    page.moveSegment(1, 0, 1);
    vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);

    page.submit();
    await fixture.whenStable();

    const request = trainingsApi.update.mock.calls[0][1] as EditTrainingRequest;
    expect(request.definitions.map((definition) => definition.id)).toEqual([null, 5]);
    expect(request.definitions[1].segments.map((segment) => segment.id)).toEqual([9, 8]);
    expect(request.definitions[1]).not.toHaveProperty('participations');
  });

  it('submits only plan fields and navigates to the trainings list', async () => {
    await createPage();
    const page = fixture.componentInstance;
    page.form.controls.name.setValue('Nowa nazwa');
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);

    page.submit();
    await fixture.whenStable();

    expect(trainingsApi.update).toHaveBeenCalledWith(12, {
      name: 'Nowa nazwa',
      trainingDate: '2026-11-11',
      definitions: [{
        id: 5,
        name: 'Główna',
        sequenceNumber: 1,
        segments: [
          { id: 8, sequenceNumber: 1, distanceM: 400, restAfterSeconds: 90 },
          { id: 9, sequenceNumber: 2, distanceM: 200, restAfterSeconds: null },
        ],
      }],
    });
    expect(navigate).toHaveBeenCalledWith(['/trainings']);
  });

  it('converts edited minute and second rest values while retaining existing rest values', async () => {
    await createPage();
    vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    const page = fixture.componentInstance;
    page.definitions.at(0).controls.segments.at(0).controls.restAfterSeconds.setValue('1,5');
    page.definitions.at(0).controls.segments.at(1).controls.restAfterSeconds.setValue('45s');

    page.submit();

    const request = trainingsApi.update.mock.calls[0][1] as EditTrainingRequest;
    expect(request.definitions[0].segments.map((segment) => segment.restAfterSeconds)).toEqual([90, 45]);
  });

  it('rejects invalid rest durations without submitting', async () => {
    await createPage();
    const page = fixture.componentInstance;
    page.definitions.at(0).controls.segments.at(0).controls.restAfterSeconds.setValue('1,001s');

    page.submit();
    fixture.detectChanges();

    expect(trainingsApi.update).not.toHaveBeenCalled();
    expect(fixture.nativeElement.textContent).toContain('Podaj minuty lub sekundy');
  });

  it('routes cancel to the trainings list', async () => {
    await createPage();

    expect(fixture.nativeElement.querySelector('footer a').getAttribute('href')).toBe('/trainings');
  });

  it('preserves form changes and displays backend validation errors', async () => {
    await createPage(() => throwError(() => new HttpErrorResponse({
      status: 400,
      error: {
        detail: 'A segment with recorded execution data cannot be removed',
        errors: { 'definitions[0].segments': 'Invalid removal' },
      },
    })));
    const page = fixture.componentInstance;
    page.form.controls.name.setValue('Moja zmiana');
    page.submit();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(page.form.controls.name.value).toBe('Moja zmiana');
    expect(page.errorMessage()).toContain('Nie można usunąć elementu');
    expect(fixture.nativeElement.textContent).toContain('definitions[0].segments: Invalid removal');
  });

  it('does not allow submission for an inactive training', async () => {
    await createPage(() => of({ id: 12 }), { ...training, active: false });

    expect(fixture.componentInstance.loadError()).toContain('Nie można edytować nieaktywnego treningu');
    expect(fixture.componentInstance.definitions.length).toBe(0);
  });
});

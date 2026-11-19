import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { AthleteResponse } from '../athletes/athlete.model';
import { AthletesApiService } from '../athletes/athletes-api.service';
import { TrainingGroup, TrainingGroupsApiService } from '../training-groups/training-groups-api.service';
import {
  FreeTextTrainingTransfer,
  TempoTrainingTransfer,
  TrainingJsonDocument,
} from './training.model';
import { TrainingsApiService } from './trainings-api.service';
import { TrainingJsonImportDialogComponent } from './training-json-import-dialog.component';

describe('TrainingJsonImportDialogComponent', () => {
  let fixture: ComponentFixture<TrainingJsonImportDialogComponent>;
  let trainingsApi: {
    validateJsonImport: ReturnType<typeof vi.fn>;
    importJson: ReturnType<typeof vi.fn>;
  };
  let athletesApi: { getActive: ReturnType<typeof vi.fn> };
  const groups: TrainingGroup[] = [
    { id: 1, name: 'Sprint' },
    { id: 2, name: 'Wytrzymałość' },
  ];
  const athletes: AthleteResponse[] = [{
    id: 10,
    firstName: 'Anna',
    lastName: 'Kowalska',
    birthYear: 2005,
    gender: 'FEMALE',
    active: true,
    trainingGroupId: 1,
  }];

  async function createDialog(): Promise<void> {
    trainingsApi = {
      validateJsonImport: vi.fn(() => of({ formatVersion: 1, trainings: [] })),
      importJson: vi.fn(() => of({ createdTrainingIds: [50], createdCount: 1 })),
    };
    athletesApi = { getActive: vi.fn(() => of(athletes)) };
    await TestBed.configureTestingModule({
      imports: [TrainingJsonImportDialogComponent],
      providers: [
        { provide: TrainingsApiService, useValue: trainingsApi },
        { provide: TrainingGroupsApiService, useValue: { getAccessible: vi.fn(() => of(groups)) } },
        { provide: AthletesApiService, useValue: athletesApi },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(TrainingJsonImportDialogComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  function showDocument(trainings: TrainingJsonDocument['trainings']): void {
    const component = fixture.componentInstance;
    component.file.set(new File(['json'], 'trainings.json', { type: 'application/json' }));
    component.document.set({ formatVersion: 1, trainings });
    component.rows.set(trainings.map((training) => ({
      training,
      selected: true,
      trainingDate: '',
      trainingGroupId: null,
      assignmentType: 'GROUP',
      athleteIds: [],
    })));
    fixture.detectChanges();
  }

  it('applies common defaults to all rows and keeps independent overrides after selection changes', async () => {
    await createDialog();
    const tempo: TempoTrainingTransfer = {
      name: 'Interwały',
      trainingType: 'TEMPO',
      definitions: [{
        name: 'Wariant A',
        segments: [{ distanceM: 400, restAfterSeconds: 60 }],
      }],
    };
    const freeText: FreeTextTrainingTransfer = {
      name: 'Mobilność',
      trainingType: 'FREE_TEXT',
      sections: [{ title: 'Rozgrzewka', content: 'Trucht' }],
      equipment: 'Gumy',
    };
    showDocument([tempo, freeText]);

    expect(fixture.nativeElement.textContent).toContain('Rozgrzewka');
    expect(fixture.nativeElement.textContent).toContain('400 m');
    expect([...fixture.nativeElement.querySelectorAll('.training-checkbox input')]
      .every((checkbox) => (checkbox as HTMLInputElement).checked)).toBe(true);

    const commonGroup = fixture.nativeElement.querySelector('#common-training-group') as HTMLSelectElement;
    commonGroup.value = '2';
    commonGroup.dispatchEvent(new Event('change'));
    const commonDate = fixture.nativeElement.querySelector('#common-training-date') as HTMLInputElement;
    commonDate.value = '2026-03-10';
    commonDate.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    expect(fixture.componentInstance.rows().map((row) => row.trainingGroupId)).toEqual([2, 2]);
    expect(fixture.componentInstance.rows().map((row) => row.trainingDate)).toEqual([
      '2026-03-10',
      '2026-03-10',
    ]);

    const firstGroup = fixture.nativeElement.querySelector('#training-group-0') as HTMLSelectElement;
    firstGroup.value = '1';
    firstGroup.dispatchEvent(new Event('change'));
    const firstCheckbox = fixture.nativeElement.querySelector(
      '[aria-label="Importuj trening: Interwały"]',
    ) as HTMLInputElement;
    firstCheckbox.click();
    fixture.detectChanges();
    expect(fixture.componentInstance.rows()[0]).toMatchObject({
      selected: false,
      trainingGroupId: 1,
      trainingDate: '2026-03-10',
    });
    expect(fixture.componentInstance.rows()[1]).toMatchObject({
      selected: true,
      trainingGroupId: 2,
      trainingDate: '2026-03-10',
    });
    expect(fixture.componentInstance.canImport()).toBe(true);

    const secondCheckbox = fixture.nativeElement.querySelector(
      '[aria-label="Importuj trening: Mobilność"]',
    ) as HTMLInputElement;
    secondCheckbox.click();
    fixture.detectChanges();
    expect(fixture.componentInstance.selectedCount()).toBe(0);
    expect(fixture.componentInstance.canImport()).toBe(false);
  });

  it('requires explicit athlete selection in FREE_TEXT ATHLETES mode and submits only selected targets', async () => {
    await createDialog();
    const freeText: FreeTextTrainingTransfer = {
      name: 'Ćwiczenia',
      trainingType: 'FREE_TEXT',
      sections: [{ title: 'Główna część', content: 'Skoki' }],
      equipment: null,
    };
    showDocument([freeText]);
    const component = fixture.componentInstance;
    const mode = fixture.nativeElement.querySelector('#assignment-mode-0') as HTMLSelectElement;
    mode.value = 'ATHLETES';
    mode.dispatchEvent(new Event('change'));
    await fixture.whenStable();
    fixture.detectChanges();
    expect(athletesApi.getActive).toHaveBeenCalledOnce();
    expect(component.canImport()).toBe(false);

    component.onAthleteIdsChange(0, [10]);
    fixture.detectChanges();
    expect(component.canImport()).toBe(true);

    component.submit();

    expect(trainingsApi.importJson).toHaveBeenCalledOnce();
    const metadata = trainingsApi.importJson.mock.calls[0][1];
    expect(metadata).toEqual({
      formatVersion: 1,
      trainings: [{
        selected: true,
        trainingDate: null,
        assignmentType: 'ATHLETES',
        trainingGroupId: null,
        athleteIds: [10],
      }],
    });
  });
});

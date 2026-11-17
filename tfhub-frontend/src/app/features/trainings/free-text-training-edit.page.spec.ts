import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, provideRouter, Router } from '@angular/router';
import { of } from 'rxjs';
import { AthletesApiService } from '../athletes/athletes-api.service';
import { TrainingGroupsApiService } from '../training-groups/training-groups-api.service';
import { FreeTextTrainingEditPage } from './free-text-training-edit.page';
import { EditFreeTextTrainingRequest } from './training.model';
import { TrainingsApiService } from './trainings-api.service';

describe('FreeTextTrainingEditPage equipment', () => {
  it('loads, edits and clears the private multiline note', async () => {
    const api = {
      getById: vi.fn(() => of({
        id: 12, name: 'Training', trainingDate: null, status: 'PLANNED',
        trainingType: 'FREE_TEXT', assignmentType: 'GROUP', trainingGroup: { id: 1, name: 'Group' },
        assignedAthletes: [], definitions: [], equipment: 'Piłka\nGumy',
        sections: [{ id: 2, title: 'Warmup', content: 'Run', sequenceNumber: 1 }],
      })),
      updateFreeText: vi.fn((_id: number, _request: EditFreeTextTrainingRequest) => of({ id: 12 })),
    };
    await TestBed.configureTestingModule({
      imports: [FreeTextTrainingEditPage],
      providers: [
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => '12' } } } },
        { provide: TrainingsApiService, useValue: api },
        { provide: TrainingGroupsApiService, useValue: { getAccessible: () => of([{ id: 1, name: 'Group' }]) } },
        { provide: AthletesApiService, useValue: { getActive: () => of([]) } },
      ],
    }).compileComponents();
    vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    const fixture = TestBed.createComponent(FreeTextTrainingEditPage);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    const textarea = fixture.nativeElement.querySelector('#equipment') as HTMLTextAreaElement;
    expect(textarea.value).toBe('Piłka\nGumy');
    textarea.value = '';
    textarea.dispatchEvent(new Event('input'));
    fixture.componentInstance.submit();
    expect(api.updateFreeText.mock.calls[0][1].equipment).toBeNull();
  });
});

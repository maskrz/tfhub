import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, provideRouter } from '@angular/router';
import { Subject, of, throwError } from 'rxjs';
import { TrainingImportDraft } from './training-import.model';
import { TrainingImportReviewPage } from './training-import-review.page';
import { TrainingImportsApiService } from './training-imports-api.service';

const draft: TrainingImportDraft = {
  importId: 21,
  status: 'REVIEW_REQUIRED',
  trainingGroup: { id: 7, name: 'Grupa A' },
  name: 'Interwały',
  trainingDate: '2026-11-10',
  recognitionMetadata: { uncertain: false },
  recognitionReviewed: false,
  definitions: [{
    id: 10, name: 'Seria', sequenceNumber: 1,
    segments: [
      { id: 11, sequenceNumber: 1, distanceM: null, restAfterSeconds: 60,
        recognitionMetadata: { uncertain: true, fields: { distanceM: { originalText: '???' } } },
        recognitionReviewed: false },
      { id: 12, sequenceNumber: 2, distanceM: 200, restAfterSeconds: null,
        recognitionMetadata: {}, recognitionReviewed: false },
    ],
    athleteRows: [{
      id: 13, recognizedName: 'Ada Kowalska', matchStatus: 'MATCHED',
      athlete: { id: 4, firstName: 'Ada', lastName: 'Kowalska', birthYear: 2000, gender: 'FEMALE', active: true },
      recognitionMetadata: {},
      executions: [
        { id: 14, segmentId: 11, segmentSequenceNumber: 1, status: 'RECORDED', measuredResult: 12.3,
          usesSpikes: true, recognizedText: '12.3', recognitionMetadata: {}, recognitionReviewed: false },
        { id: 15, segmentId: 12, segmentSequenceNumber: 2, status: 'PENDING', measuredResult: null,
          usesSpikes: false, recognizedText: null, recognitionMetadata: {}, recognitionReviewed: false },
      ],
    }],
  }],
  validation: { readyForAcceptance: false, issues: [
    { code: 'SEGMENT_DISTANCE_UNRESOLVED', path: 'definitions/10/segments/1/distanceM', message: 'Distance required' },
  ] },
};

describe('TrainingImportReviewPage', () => {
  let fixture: ComponentFixture<TrainingImportReviewPage>;
  let api: {
    getDraft: ReturnType<typeof vi.fn>;
    getCandidates: ReturnType<typeof vi.fn>;
    sourceImageUrl: ReturnType<typeof vi.fn>;
    updateDraft: ReturnType<typeof vi.fn>;
    accept: ReturnType<typeof vi.fn>;
  };
  let router: Router;

  async function createPage(currentDraft: TrainingImportDraft = draft): Promise<void> {
    api = {
      getDraft: vi.fn(() => of(currentDraft)),
      getCandidates: vi.fn(() => of([currentDraft.definitions[0].athleteRows[0].athlete])),
      sourceImageUrl: vi.fn(() => '/api/training-imports/21/source-image'),
      updateDraft: vi.fn(() => of(draft)),
      accept: vi.fn(() => of({ importId: 21, status: 'ACCEPTED', trainingId: 99 })),
    };
    await TestBed.configureTestingModule({
      imports: [TrainingImportReviewPage],
      providers: [
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => '21' } } } },
        { provide: TrainingImportsApiService, useValue: api },
      ],
    }).compileComponents();
    router = TestBed.inject(Router);
    fixture = TestBed.createComponent(TrainingImportReviewPage);
    fixture.detectChanges();
  }

  it('displays unresolved distance and preserves other values in the corrected update', async () => {
    await createPage();
    expect(fixture.nativeElement.textContent).toContain('Nie rozpoznano dystansu');
    expect(fixture.nativeElement.textContent).toContain('???');
    expect(fixture.nativeElement.textContent).toContain('Ada Kowalska');
    const distance = fixture.nativeElement.querySelector('input[formControlName=distanceM]') as HTMLInputElement;
    distance.value = '400';
    distance.dispatchEvent(new Event('input'));
    const reviewed = fixture.nativeElement.querySelector('input[formControlName=recognitionReviewed]') as HTMLInputElement;
    reviewed.click();
    fixture.nativeElement.querySelector('form').dispatchEvent(new Event('submit'));
    fixture.detectChanges();

    expect(api.updateDraft).toHaveBeenCalledOnce();
    const request = api.updateDraft.mock.calls[0][1];
    expect(request.definitions[0].segments).toEqual([
      { id: 11, sequenceNumber: 1, distanceM: 400, restAfterSeconds: 60, recognitionReviewed: true },
      { id: 12, sequenceNumber: 2, distanceM: 200, restAfterSeconds: null, recognitionReviewed: false },
    ]);
    expect(request.definitions[0].athleteRows[0].executions[0].measuredResult).toBe(12.3);
    expect(request.definitions[0].athleteRows[0].athleteId).toBe(4);
  });

  it('preserves edits on save failure and prevents duplicate saves', async () => {
    await createPage();
    const pending = new Subject<TrainingImportDraft>();
    api.updateDraft.mockReturnValue(pending);
    const distance = fixture.nativeElement.querySelector('input[formControlName=distanceM]') as HTMLInputElement;
    distance.value = '300';
    distance.dispatchEvent(new Event('input'));
    fixture.componentInstance.save();
    fixture.componentInstance.save();
    expect(api.updateDraft).toHaveBeenCalledOnce();
    pending.error(new Error('network'));
    fixture.detectChanges();
    expect(distance.value).toBe('300');
    expect(fixture.nativeElement.textContent).toContain('Nie udało się zapisać');
  });

  it('only accepts a saved ready draft and prevents duplicate acceptance', async () => {
    await createPage();
    const pending = new Subject<{ importId: number; status: 'ACCEPTED'; trainingId: number }>();
    const ready = structuredClone(draft);
    ready.validation = { readyForAcceptance: true, issues: [] };
    ready.definitions[0].segments[0].distanceM = 400;
    ready.definitions[0].segments[0].recognitionReviewed = true;
    api.updateDraft.mockReturnValue(of(ready));
    api.accept.mockReturnValue(pending);
    const navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);
    fixture.componentInstance.accept();
    expect(api.accept).not.toHaveBeenCalled();
    fixture.componentInstance.save();
    fixture.componentInstance.accept();
    fixture.componentInstance.accept();
    expect(api.accept).toHaveBeenCalledOnce();
    pending.next({ importId: 21, status: 'ACCEPTED', trainingId: 99 });
    pending.complete();
    expect(navigate).toHaveBeenCalledWith(['/trainings', 99]);
  });

  it('allows correction of a recognized but uncertain distance', async () => {
    const uncertain = structuredClone(draft);
    uncertain.definitions[0].segments[0].distanceM = 350;
    await createPage(uncertain);
    expect(fixture.nativeElement.textContent).toContain('Rozpoznany dystans wymaga weryfikacji');
    const distance = fixture.nativeElement.querySelector('input[formControlName=distanceM]') as HTMLInputElement;
    distance.value = '400';
    distance.dispatchEvent(new Event('input'));
    (fixture.nativeElement.querySelector('input[formControlName=recognitionReviewed]') as HTMLInputElement).click();
    fixture.componentInstance.save();
    expect(api.updateDraft.mock.calls[0][1].definitions[0].segments[0].distanceM).toBe(400);
    expect(api.updateDraft.mock.calls[0][1].definitions[0].segments[0].recognitionReviewed).toBe(true);
  });

  it('shows an error when saving fails', async () => {
    await createPage();
    api.updateDraft.mockReturnValue(throwError(() => new Error('network')));
    fixture.componentInstance.save();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Nie udało się zapisać');
  });
});

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
    reject: ReturnType<typeof vi.fn>;
  };
  let router: Router;

  async function createPage(currentDraft: TrainingImportDraft = draft): Promise<void> {
    api = {
      getDraft: vi.fn(() => of(currentDraft)),
      getCandidates: vi.fn(() => of([draft.definitions[0].athleteRows[0].athlete])),
      sourceImageUrl: vi.fn(() => '/api/training-imports/21/source-image'),
      updateDraft: vi.fn(() => of(draft)),
      accept: vi.fn(() => of({ importId: 21, status: 'ACCEPTED', trainingId: 99 })),
      reject: vi.fn(() => of({ importId: 21, status: 'REJECTED' })),
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
    expect(fixture.nativeElement.textContent).not.toContain('Odczyt:');
    expect((fixture.nativeElement.querySelector('[role=combobox]') as HTMLInputElement).value).toContain('Ada Kowalska');
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

  it('shows editable segment and athlete results tables with review highlights', async () => {
    const unresolved = structuredClone(draft);
    unresolved.recognitionMetadata = { uncertain: true, fields: { name: { uncertain: true } } };
    unresolved.definitions[0].athleteRows[0].matchStatus = 'UNRESOLVED';
    unresolved.definitions[0].athleteRows[0].athlete = null;
    unresolved.definitions[0].athleteRows[0].executions[0].usesSpikes = null;
    await createPage(unresolved);
    const host = fixture.nativeElement as HTMLElement;
    expect(host.querySelectorAll('.segments-table tbody tr')).toHaveLength(2);
    expect(host.querySelectorAll('.executions-table tbody tr')).toHaveLength(1);
    expect(host.querySelectorAll('.executions-table tbody td')).toHaveLength(2);
    expect(host.querySelector('.segments-table tbody tr:first-child')?.classList.contains('needs-review')).toBe(true);
    expect(host.querySelector('.segments-table tbody tr:nth-child(2)')?.classList.contains('needs-review')).toBe(false);
    expect(host.querySelector('.executions-table tbody th')?.classList.contains('needs-review')).toBe(true);
    expect(host.querySelector('.executions-table tbody td')?.classList.contains('needs-review')).toBe(true);
    expect(host.querySelector('input[formControlName=name]')?.closest('label')?.classList.contains('needs-review')).toBe(true);
    expect(host.textContent).toContain('Wymaga przypisania lub pominięcia');
    expect(host.textContent).not.toContain('Wymaga uzupełnienia lub weryfikacji');

    const athlete = host.querySelector('[role=combobox]') as HTMLInputElement;
    athlete.dispatchEvent(new FocusEvent('focus'));
    fixture.detectChanges();
    (host.querySelector('[role=option]') as HTMLButtonElement).click();
    (host.querySelector('.spikes-control input') as HTMLInputElement).click();
    const distance = host.querySelector('input[formControlName=distanceM]') as HTMLInputElement;
    distance.value = '400';
    distance.dispatchEvent(new Event('input'));
    (host.querySelector('.segments-table input[formControlName=recognitionReviewed]') as HTMLInputElement).click();
    (host.querySelector('.training-summary input[formControlName=recognitionReviewed]') as HTMLInputElement).click();
    fixture.detectChanges();
    expect(host.querySelector('.segments-table tbody tr:first-child')?.classList.contains('needs-review')).toBe(false);
    expect(host.querySelector('.executions-table tbody th')?.classList.contains('needs-review')).toBe(false);
    expect(host.querySelector('.executions-table tbody td')?.classList.contains('needs-review')).toBe(false);
    expect(host.querySelector('input[formControlName=name]')?.closest('label')?.classList.contains('needs-review')).toBe(false);
  });

  it('highlights duplicate athlete assignments and uncertain executions', async () => {
    const duplicated = structuredClone(draft);
    const secondRow = structuredClone(duplicated.definitions[0].athleteRows[0]);
    secondRow.id = 99;
    secondRow.recognizedName = 'Inna osoba';
    duplicated.definitions[0].athleteRows.push(secondRow);
    duplicated.definitions[0].athleteRows[0].executions[0].recognitionMetadata = { uncertain: true };
    await createPage(duplicated);
    const host = fixture.nativeElement as HTMLElement;
    expect(host.querySelectorAll('.executions-table tbody th.needs-review')).toHaveLength(2);
    expect(host.textContent).toContain('Zawodnik jest przypisany więcej niż raz');
    expect(host.querySelector('.executions-table tbody td')?.classList.contains('needs-review')).toBe(true);
    expect(host.querySelector('.executions-table tbody td:nth-of-type(2)')?.classList.contains('needs-review')).toBe(false);
    (host.querySelectorAll('.executions-table tbody th .skip-button')[1] as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(host.querySelectorAll('.executions-table tbody th.needs-review')).toHaveLength(0);
  });

  it('shows unmatched names in the selector and only offers eligible athletes', async () => {
    const unmatched = structuredClone(draft);
    unmatched.definitions[0].athleteRows[0].matchStatus = 'UNRESOLVED';
    unmatched.definitions[0].athleteRows[0].athlete = null;
    await createPage(unmatched);
    const host = fixture.nativeElement as HTMLElement;
    const input = host.querySelector('[role=combobox]') as HTMLInputElement;
    expect(input.value).toBe('Ada Kowalska');
    expect(input.classList.contains('searchable-select__input--unmatched')).toBe(true);
    input.dispatchEvent(new FocusEvent('focus'));
    fixture.detectChanges();
    expect([...host.querySelectorAll('[role=option]')].map((option) => option.textContent?.trim()))
      .toEqual(['Ada Kowalska (2000)']);
    (host.querySelector('[role=option]') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(fixture.componentInstance.rows(0).at(0).controls.matchStatus.value).toBe('MATCHED');
    expect(fixture.componentInstance.rows(0).at(0).controls.athleteId.value).toBe(4);
    expect(host.querySelector('.executions-table tbody th')?.classList.contains('needs-review')).toBe(false);
  });

  it('resets loaded values and retains reset baselines after saving with new draft IDs', async () => {
    await createPage();
    const name = fixture.componentInstance.form.controls.name;
    const segment = fixture.componentInstance.segments(0).at(1);
    const execution = fixture.componentInstance.executions(0, 0).at(0);
    name.setValue('Zmieniony');
    segment.controls.distanceM.setValue(500);
    execution.controls.timeInput.setValue('1:02,345');
    fixture.componentInstance.resultChanged(execution);
    fixture.detectChanges();
    expect(fixture.componentInstance.changed(name)).toBe(true);
    expect(execution.controls.measuredResult.value).toBe(62.345);
    const saved = structuredClone(draft);
    saved.name = 'Zmieniony';
    saved.definitions[0].id = 100;
    saved.definitions[0].segments[1].id = 120;
    saved.definitions[0].segments[1].distanceM = 500;
    saved.definitions[0].athleteRows[0].id = 130;
    saved.definitions[0].athleteRows[0].executions[0].id = 140;
    saved.definitions[0].athleteRows[0].executions[0].measuredResult = 62.345;
    saved.definitions[0].athleteRows[0].executions[1].segmentId = 120;
    api.updateDraft.mockReturnValue(of(saved));
    fixture.componentInstance.save();
    fixture.detectChanges();
    const updatedSegment = fixture.componentInstance.segments(0).at(1);
    const updatedExecution = fixture.componentInstance.executions(0, 0).at(0);
    expect(fixture.componentInstance.changed(updatedSegment.controls.distanceM)).toBe(true);
    expect(fixture.componentInstance.changed(updatedExecution.controls.timeInput)).toBe(true);
    fixture.componentInstance.reset(name);
    fixture.componentInstance.reset(updatedSegment.controls.distanceM);
    fixture.componentInstance.resetTime(updatedExecution);
    expect(name.value).toBe('Interwały');
    expect(updatedSegment.controls.distanceM.value).toBe(200);
    expect(updatedExecution.controls.measuredResult.value).toBe(12.3);
    expect(updatedExecution.controls.status.value).toBe('RECORDED');
    expect(fixture.componentInstance.form.dirty).toBe(true);
  });

  it('maps completion input to status and preserves unresolved spike usage until confirmed', async () => {
    const unresolved = structuredClone(draft);
    unresolved.definitions[0].athleteRows[0].executions[0].usesSpikes = null;
    unresolved.definitions[0].athleteRows[0].executions[0].measuredResult = 12.345;
    await createPage(unresolved);
    const execution = fixture.componentInstance.executions(0, 0).at(0);
    expect(execution.controls.timeInput.value).toBe('12,345');
    const input = fixture.nativeElement.querySelector('input[formControlName=timeInput]') as HTMLInputElement;
    input.value = 'X';
    input.dispatchEvent(new Event('input'));
    expect(execution.controls.status.value).toBe('COMPLETED_WITHOUT_RESULT');
    expect(execution.controls.measuredResult.value).toBeNull();
    expect(execution.controls.usesSpikes.value).toBeNull();
    input.value = '-';
    input.dispatchEvent(new Event('input'));
    expect(execution.controls.status.value).toBe('NOT_COMPLETED');
    input.value = '';
    input.dispatchEvent(new Event('input'));
    expect(execution.controls.status.value).toBe('PENDING');
    input.value = 'abc';
    input.dispatchEvent(new Event('input'));
    expect(execution.controls.timeInput.invalid).toBe(true);
    fixture.componentInstance.resetTime(execution);
    expect(execution.controls.measuredResult.value).toBe(12.345);
    expect(execution.controls.status.value).toBe('RECORDED');
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
    expect(fixture.nativeElement.textContent).toContain('wymaga weryfikacji');
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

  it('moves segments together with their executions and builds the corresponding update', async () => {
    await createPage();
    fixture.componentInstance.moveSegment(0, 0, 1);
    fixture.componentInstance.save();
    const request = api.updateDraft.mock.calls[0][1].definitions[0];
    expect(request.segments.map((segment: { id: number; sequenceNumber: number }) =>
      [segment.id, segment.sequenceNumber])).toEqual([[12, 1], [11, 2]]);
    expect(request.athleteRows[0].executions.map((execution: { id: number; segmentSequenceNumber: number }) =>
      [execution.id, execution.segmentSequenceNumber])).toEqual([[15, 1], [14, 2]]);
    expect(request.athleteRows[0].executions[1].measuredResult).toBe(12.3);
  });

  it('preserves non-contiguous sequence numbers when structure is unchanged', async () => {
    const nonContiguous = structuredClone(draft);
    nonContiguous.definitions[0].sequenceNumber = 3;
    nonContiguous.definitions[0].segments[0].sequenceNumber = 2;
    nonContiguous.definitions[0].segments[1].sequenceNumber = 5;
    nonContiguous.definitions[0].athleteRows[0].executions[0].segmentSequenceNumber = 2;
    nonContiguous.definitions[0].athleteRows[0].executions[1].segmentSequenceNumber = 5;
    await createPage(nonContiguous);
    fixture.componentInstance.save();
    const definition = api.updateDraft.mock.calls[0][1].definitions[0];
    expect(definition.sequenceNumber).toBe(3);
    expect(definition.segments.map((segment: { sequenceNumber: number }) => segment.sequenceNumber)).toEqual([2, 5]);
    expect(definition.athleteRows[0].executions.map((execution: { segmentSequenceNumber: number }) =>
      execution.segmentSequenceNumber)).toEqual([2, 5]);
  });

  it('adds and deletes segments without losing unrelated executions', async () => {
    await createPage();
    fixture.componentInstance.addSegment(0);
    fixture.componentInstance.requestRemoveSegment(0, 0);
    expect(fixture.componentInstance.segments(0).length).toBe(3);
    fixture.componentInstance.confirmDialogAction();
    fixture.componentInstance.save();
    const definition = api.updateDraft.mock.calls[0][1].definitions[0];
    expect(definition.segments.map((segment: { id: number | null }) => segment.id)).toEqual([12, null]);
    expect(definition.athleteRows[0].executions.map((execution: { id: number | null }) => execution.id))
      .toEqual([15, null]);
    expect(definition.athleteRows[0].executions[1].segmentSequenceNumber).toBe(3);
    expect(definition.athleteRows[0].executions[1].status).toBe('PENDING');
  });

  it('adds an existing athlete to a new subgroup with an execution for each segment', async () => {
    await createPage();
    fixture.componentInstance.addDefinition();
    fixture.componentInstance.definitions.at(1).controls.name.setValue('Nowa podgrupa');
    fixture.componentInstance.segments(1).at(0).controls.distanceM.setValue(100);
    fixture.componentInstance.addSegment(1);
    fixture.componentInstance.segments(1).at(1).controls.distanceM.setValue(200);
    fixture.detectChanges();

    const newSubgroup = fixture.nativeElement.querySelectorAll('.definition-card')[1] as HTMLElement;
    ([...newSubgroup.querySelectorAll('button')].find((button) => button.textContent?.trim() === 'Dodaj zawodnika') as HTMLButtonElement).click();
    fixture.detectChanges();
    const row = fixture.componentInstance.rows(1).at(0);
    const selector = newSubgroup.querySelector('[role=combobox]') as HTMLInputElement;
    selector.dispatchEvent(new FocusEvent('focus'));
    fixture.detectChanges();
    (newSubgroup.querySelector('[role=option]') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(row.controls.id.value).toBeNull();
    expect(row.controls.recognizedName.value).toBe('Ada Kowalska');
    expect(fixture.componentInstance.hasDuplicateAthlete(row)).toBe(true);

    fixture.componentInstance.skipAthlete(fixture.componentInstance.rows(0).at(0));
    const firstExecution = fixture.componentInstance.executions(1, 0).at(0);
    firstExecution.controls.timeInput.setValue('12,34');
    fixture.componentInstance.resultChanged(firstExecution);
    fixture.componentInstance.moveSegment(1, 0, 1);
    fixture.componentInstance.save();

    const subgroup = api.updateDraft.mock.calls[0][1].definitions[1];
    expect(subgroup.athleteRows[0]).toEqual({
      id: null, recognizedName: 'Ada Kowalska', athleteId: 4, matchStatus: 'MATCHED',
      executions: [
        { id: null, segmentSequenceNumber: 1, status: 'PENDING', measuredResult: null, usesSpikes: false, recognitionReviewed: false },
        { id: null, segmentSequenceNumber: 2, status: 'RECORDED', measuredResult: 12.34, usesSpikes: false, recognitionReviewed: false },
      ],
    });
  });

  it('allows cancelling a new athlete row without modifying existing rows', async () => {
    await createPage();
    fixture.componentInstance.addAthlete(0);
    expect(fixture.componentInstance.rows(0).length).toBe(2);
    fixture.componentInstance.removeNewAthlete(0, 1);
    fixture.componentInstance.removeNewAthlete(0, 0);
    expect(fixture.componentInstance.rows(0).length).toBe(1);
    expect(fixture.componentInstance.rows(0).at(0).controls.id.value).toBe(13);
  });

  it('adds, reorders and removes definitions only after confirmation', async () => {
    await createPage();
    fixture.componentInstance.addDefinition();
    fixture.componentInstance.definitions.at(1).controls.name.setValue('Druga seria');
    fixture.componentInstance.moveDefinition(1, -1);
    fixture.componentInstance.save();
    let definitions = api.updateDraft.mock.calls[0][1].definitions;
    expect(definitions.map((definition: { id: number | null }) => definition.id)).toEqual([null, 10]);
    expect(definitions[1].athleteRows[0].executions[0].id).toBe(14);

    fixture.componentInstance.addDefinition();
    fixture.componentInstance.requestRemoveDefinition(0);
    fixture.componentInstance.closeDialog();
    expect(fixture.componentInstance.definitions.length).toBe(2);
    fixture.componentInstance.requestRemoveDefinition(1);
    fixture.componentInstance.confirmDialogAction();
    expect(fixture.componentInstance.definitions.length).toBe(1);
    fixture.componentInstance.requestRemoveDefinition(0);
    expect(fixture.componentInstance.dialogAction()).toBeNull();
  });

  it('rejects only after confirmation and blocks duplicate or competing actions', async () => {
    await createPage();
    const pending = new Subject<{ importId: number; status: 'REJECTED' }>();
    api.reject.mockReturnValue(pending);
    fixture.componentInstance.confirmReject();
    fixture.componentInstance.closeDialog();
    expect(api.reject).not.toHaveBeenCalled();
    fixture.componentInstance.confirmReject();
    fixture.componentInstance.confirmDialogAction();
    fixture.componentInstance.confirmDialogAction();
    fixture.componentInstance.save();
    expect(api.reject).toHaveBeenCalledOnce();
    expect(api.updateDraft).not.toHaveBeenCalled();
    expect(fixture.componentInstance.rejected()).toBe(false);
    pending.next({ importId: 21, status: 'REJECTED' });
    pending.complete();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Import został odrzucony');
    expect(fixture.nativeElement.textContent).toContain('Wróć do listy treningów');
  });

  it('opens and cancels rejection using the visible controls without making a request', async () => {
    await createPage();
    const button = [...fixture.nativeElement.querySelectorAll('button')]
      .find((element) => (element as HTMLButtonElement).textContent?.trim() === 'Odrzuć import') as HTMLButtonElement;
    button.click();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('dialog').hasAttribute('open')).toBe(true);
    const cancel = [...fixture.nativeElement.querySelectorAll('dialog button')]
      .find((element) => (element as HTMLButtonElement).textContent?.trim() === 'Anuluj') as HTMLButtonElement;
    cancel.click();
    expect(api.reject).not.toHaveBeenCalled();
    expect(fixture.componentInstance.dialogAction()).toBeNull();
  });

  it('keeps unsaved data and permits retry when rejection fails', async () => {
    await createPage();
    api.reject.mockReturnValue(throwError(() => new Error('network')));
    fixture.componentInstance.definitions.at(0).controls.name.setValue('Nowa nazwa');
    fixture.componentInstance.confirmReject();
    fixture.componentInstance.confirmDialogAction();
    fixture.detectChanges();
    expect(fixture.componentInstance.rejected()).toBe(false);
    expect(fixture.componentInstance.definitions.at(0).controls.name.value).toBe('Nowa nazwa');
    expect(fixture.nativeElement.textContent).toContain('Nie udało się odrzucić');
  });

  it('shows localized validation issues with their definition and segment', async () => {
    await createPage();
    expect(fixture.nativeElement.textContent).toContain('Wariant 1, odcinek 1');
    expect(fixture.nativeElement.textContent).toContain('Uzupełnij dodatni dystans odcinka');
  });
});

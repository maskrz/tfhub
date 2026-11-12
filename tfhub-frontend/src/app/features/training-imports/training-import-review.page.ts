import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, ElementRef, OnInit, ViewChild, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { AbstractControl, FormArray, FormControl, FormGroup, ReactiveFormsModule, ValidatorFn, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { EMPTY, catchError, finalize, forkJoin, take } from 'rxjs';
import { SearchableSelectComponent, SearchableSelectOption } from '../../shared/searchable-select.component';
import { formatResult, normalizeTimeInput, parseTimeSeconds } from '../trainings/training-time';
import {
  ImportCandidate, ImportDefinition, ImportExecution, ImportExecutionStatus, ImportMatchStatus,
  ImportSegment, TrainingImportDraft, TrainingImportDraftUpdate,
} from './training-import.model';
import { TrainingImportsApiService } from './training-imports-api.service';

type Metadata = Record<string, unknown>;
type SegmentForm = FormGroup<{
  id: FormControl<number | null>;
  originalSequence: FormControl<number>;
  metadata: FormControl<Metadata>;
  distanceM: FormControl<number | null>;
  restAfterSeconds: FormControl<number | null>;
  recognitionReviewed: FormControl<boolean>;
}>;
type ExecutionForm = FormGroup<{
  id: FormControl<number | null>;
  recognizedText: FormControl<string | null>;
  metadata: FormControl<Metadata>;
  status: FormControl<ImportExecutionStatus>;
  measuredResult: FormControl<number | null>;
  timeInput: FormControl<string>;
  usesSpikes: FormControl<boolean | null>;
  recognitionReviewed: FormControl<boolean>;
}>;
type RowForm = FormGroup<{
  id: FormControl<number | null>;
  recognizedName: FormControl<string>;
  metadata: FormControl<Metadata>;
  matchStatus: FormControl<ImportMatchStatus>;
  athleteId: FormControl<number | null>;
  athleteSelection: FormControl<string>;
  executions: FormArray<ExecutionForm>;
}>;
type DefinitionForm = FormGroup<{
  id: FormControl<number | null>;
  originalSequence: FormControl<number>;
  name: FormControl<string>;
  segments: FormArray<SegmentForm>;
  athleteRows: FormArray<RowForm>;
}>;
type DialogAction = 'accept' | 'reject' | 'remove-definition' | 'remove-segment';

const integerPattern = Validators.pattern(/^\d+$/);
const nonBlank: ValidatorFn = (control) =>
  typeof control.value === 'string' && control.value.trim() ? null : { required: true };
const timeValidator: ValidatorFn = (control) => {
  const value = String(control.value).trim();
  if (!value || value.toUpperCase() === 'X' || value === '-') return null;
  const seconds = parseTimeSeconds(value, 3);
  return seconds !== null && seconds <= 999999999.999 ? null : { time: true };
};

@Component({
  selector: 'app-training-import-review-page',
  imports: [ReactiveFormsModule, RouterLink, SearchableSelectComponent],
  templateUrl: './training-import-review.page.html',
  styleUrl: './training-import-review.page.scss',
})
export class TrainingImportReviewPage implements OnInit {
  @ViewChild('confirmDialog') private confirmDialog?: ElementRef<HTMLDialogElement>;

  readonly draft = signal<TrainingImportDraft | null>(null);
  readonly candidates = signal<ImportCandidate[]>([]);
  readonly loading = signal(true);
  readonly saving = signal(false);
  readonly accepting = signal(false);
  readonly rejecting = signal(false);
  readonly rejected = signal(false);
  readonly imageError = signal(false);
  readonly error = signal<string | null>(null);
  readonly message = signal<string | null>(null);
  readonly dialogAction = signal<DialogAction | null>(null);
  readonly form = new FormGroup({
    name: new FormControl('', { nonNullable: true, validators: [nonBlank, Validators.maxLength(255)] }),
    trainingDate: new FormControl('', { nonNullable: true, validators: Validators.required }),
    recognitionReviewed: new FormControl(false, { nonNullable: true }),
    definitions: new FormArray<DefinitionForm>([]),
  });

  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly api = inject(TrainingImportsApiService);
  private readonly destroyRef = inject(DestroyRef);
  private importId = 0;
  private readonly initialValues = new WeakMap<object, unknown>();
  private initialized = false;
  private pendingRemoval: { definition: number; segment?: number } | null = null;

  get definitions(): FormArray<DefinitionForm> { return this.form.controls.definitions; }
  busy(): boolean { return this.saving() || this.accepting() || this.rejecting(); }

  ngOnInit(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    if (!Number.isSafeInteger(id) || id <= 0) {
      this.error.set('Nieprawidłowy identyfikator importu.');
      this.loading.set(false);
      return;
    }
    this.importId = id;
    forkJoin({ draft: this.api.getDraft(id), candidates: this.api.getCandidates(id) }).pipe(
      take(1),
      catchError((error: unknown) => {
        this.error.set(this.requestError(error, 'Nie udało się pobrać importu.'));
        return EMPTY;
      }),
      finalize(() => this.loading.set(false)),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe(({ draft, candidates }) => {
      if (draft.status !== 'REVIEW_REQUIRED') {
        this.error.set('Ten import nie jest dostępny do weryfikacji. Sprawdź jego status na liście treningów.');
        return;
      }
      this.candidates.set(candidates);
      this.setDraft(draft);
    });
  }

  imageUrl(): string { return this.api.sourceImageUrl(this.importId); }
  segments(definition: number): FormArray<SegmentForm> { return this.definitions.at(definition).controls.segments; }
  rows(definition: number): FormArray<RowForm> { return this.definitions.at(definition).controls.athleteRows; }
  executions(definition: number, row: number): FormArray<ExecutionForm> {
    return this.rows(definition).at(row).controls.executions;
  }
  isUncertain(metadata: Metadata): boolean { return metadata['uncertain'] === true; }

  needsTrainingReview(): boolean {
    return this.isUncertain(this.draft()?.recognitionMetadata ?? {}) && !this.form.controls.recognitionReviewed.value;
  }

  needsDefinitionReview(): boolean {
    return this.needsTrainingReview() && this.originalDefinitions().some((item) => this.isUncertain(item.metadata));
  }

  needsSegmentReview(segment: SegmentForm): boolean {
    return segment.controls.distanceM.value === null || segment.controls.distanceM.invalid
      || segment.controls.restAfterSeconds.invalid
      || (this.isUncertain(segment.controls.metadata.value) && !segment.controls.recognitionReviewed.value);
  }

  needsAthleteReview(row: RowForm): boolean {
    return row.controls.matchStatus.value === 'UNRESOLVED'
      || (row.controls.matchStatus.value === 'MATCHED'
        && (row.controls.athleteId.value === null
          || !this.candidates().some((candidate) => candidate.id === row.controls.athleteId.value)
          || this.hasDuplicateAthlete(row)));
  }

  hasDuplicateAthlete(row: RowForm): boolean {
    const athleteId = row.controls.athleteId.value;
    return athleteId !== null && row.controls.matchStatus.value === 'MATCHED'
      && this.definitions.controls.flatMap((definition) => definition.controls.athleteRows.controls)
        .some((other) => other !== row && other.controls.matchStatus.value === 'MATCHED'
          && other.controls.athleteId.value === athleteId);
  }

  candidateName(id: number | null): string | null {
    const candidate = this.candidates().find((item) => item.id === id);
    return candidate ? `${candidate.firstName} ${candidate.lastName}` : null;
  }

  needsExecutionReview(execution: ExecutionForm): boolean {
    return execution.controls.usesSpikes.value === null || execution.controls.measuredResult.invalid
      || execution.controls.timeInput.invalid
      || (execution.controls.status.value === 'RECORDED') !== (execution.controls.measuredResult.value !== null)
      || (this.isUncertain(execution.controls.metadata.value) && !execution.controls.recognitionReviewed.value);
  }

  metadataText(metadata: Metadata, key: string): string | null {
    const value = metadata[key];
    return typeof value === 'string' && value.trim() ? value : null;
  }

  fieldMetadata(metadata: Metadata, field: string): Metadata {
    const fields = metadata['fields'];
    if (!fields || typeof fields !== 'object' || !(field in fields)) return {};
    const value = fields[field as keyof typeof fields];
    return value && typeof value === 'object' && !Array.isArray(value) ? value as Metadata : {};
  }

  originalDefinitions(): { sequence: number; metadata: Metadata }[] {
    const definitions = this.draft()?.recognitionMetadata['definitions'];
    if (!Array.isArray(definitions)) return [];
    return definitions.flatMap((item: unknown) => {
      if (!item || typeof item !== 'object' || !('sequenceNumber' in item) || typeof item.sequenceNumber !== 'number'
        || !('name' in item) || !item.name || typeof item.name !== 'object' || Array.isArray(item.name)) return [];
      return [{ sequence: item.sequenceNumber, metadata: item.name as Metadata }];
    });
  }

  candidateOptions(): SearchableSelectOption[] {
    return this.candidates().map((candidate) => ({
      value: String(candidate.id),
      label: `${candidate.firstName} ${candidate.lastName}${candidate.birthYear ? ` (${candidate.birthYear})` : ''}${candidate.active ? '' : ' — nieaktywny'}`,
    }));
  }

  selectAthlete(row: RowForm, value: string): void {
    row.controls.athleteId.setValue(value ? Number(value) : null);
    row.controls.matchStatus.setValue(value ? 'MATCHED' : 'UNRESOLVED');
    if (row.controls.id.value === null) {
      row.controls.recognizedName.setValue(this.candidateName(row.controls.athleteId.value) ?? '');
    }
  }

  addAthlete(definition: number): void {
    if (this.busy()) return;
    this.rows(definition).push(new FormGroup({
      id: new FormControl<number | null>(null),
      recognizedName: new FormControl('', { nonNullable: true }),
      metadata: new FormControl<Metadata>({}, { nonNullable: true }),
      matchStatus: new FormControl<ImportMatchStatus>('UNRESOLVED', { nonNullable: true }),
      athleteId: new FormControl<number | null>(null),
      athleteSelection: new FormControl('', { nonNullable: true }),
      executions: new FormArray(this.segments(definition).controls.map(() => this.executionForm(undefined, false))),
    }));
    this.form.markAsDirty();
  }

  removeNewAthlete(definition: number, row: number): void {
    if (this.busy() || this.rows(definition).at(row).controls.id.value !== null) return;
    this.rows(definition).removeAt(row);
    this.form.markAsDirty();
  }

  skipAthlete(row: RowForm): void {
    row.controls.athleteSelection.setValue('');
    row.controls.athleteId.setValue(null);
    row.controls.matchStatus.setValue('SKIPPED');
    this.form.markAsDirty();
  }

  resetAthlete(row: RowForm): void {
    this.reset(row.controls.athleteSelection);
    this.reset(row.controls.athleteId);
    this.reset(row.controls.matchStatus);
  }

  changed<T>(control: FormControl<T>): boolean {
    return this.initialValues.has(control) && !Object.is(control.value, this.initialValues.get(control));
  }

  reset<T>(control: FormControl<T>): void {
    if (!this.initialValues.has(control)) return;
    control.setValue(this.initialValues.get(control) as T);
    control.markAsDirty();
    this.form.markAsDirty();
  }

  resultChanged(execution: ExecutionForm): void {
    const text = execution.controls.timeInput.value.trim();
    const status: ImportExecutionStatus = text.toUpperCase() === 'X' ? 'COMPLETED_WITHOUT_RESULT'
      : text === '-' ? 'NOT_COMPLETED' : text ? 'RECORDED' : 'PENDING';
    execution.controls.status.setValue(status);
    execution.controls.measuredResult.setValue(status === 'RECORDED' ? parseTimeSeconds(text, 3) : null);
  }

  formatTime(execution: ExecutionForm): void {
    const normalized = normalizeTimeInput(execution.controls.timeInput.value, 3);
    if (normalized !== null) execution.controls.timeInput.setValue(normalized);
  }

  resetTime(execution: ExecutionForm): void {
    this.reset(execution.controls.timeInput);
    this.reset(execution.controls.status);
    this.reset(execution.controls.measuredResult);
  }

  timeChanged(execution: ExecutionForm): boolean {
    return this.changed(execution.controls.timeInput) || this.changed(execution.controls.status)
      || this.changed(execution.controls.measuredResult);
  }

  setSpikes(execution: ExecutionForm, event: Event): void {
    execution.controls.usesSpikes.setValue((event.target as HTMLInputElement).checked);
    this.form.markAsDirty();
  }

  addDefinition(): void {
    if (this.busy()) return;
    this.definitions.push(this.definitionForm());
    this.form.markAsDirty();
  }

  moveDefinition(index: number, offset: number): void {
    this.move(this.definitions, index, offset);
  }

  addSegment(definition: number): void {
    if (this.busy()) return;
    this.segments(definition).push(this.segmentForm());
    for (const row of this.rows(definition).controls) row.controls.executions.push(this.executionForm());
    this.form.markAsDirty();
  }

  moveSegment(definition: number, index: number, offset: number): void {
    const target = index + offset;
    if (this.busy() || target < 0 || target >= this.segments(definition).length) return;
    this.move(this.segments(definition), index, offset);
    for (const row of this.rows(definition).controls) this.move(row.controls.executions, index, offset);
  }

  requestRemoveDefinition(index: number): void {
    if (this.busy() || this.definitions.length <= 1) return;
    this.pendingRemoval = { definition: index };
    this.openDialog('remove-definition');
  }

  requestRemoveSegment(definition: number, segment: number): void {
    if (this.busy() || this.segments(definition).length <= 1) return;
    this.pendingRemoval = { definition, segment };
    this.openDialog('remove-segment');
  }

  save(): void {
    if (!this.draft() || this.busy() || this.rejected()) return;
    this.message.set(null);
    this.error.set(null);
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.error.set('Sprawdź dane formularza: nazwy, datę, dystanse i przerwy.');
      return;
    }
    this.saving.set(true);
    this.api.updateDraft(this.importId, this.toRequest()).pipe(
      take(1),
      catchError((error: unknown) => {
        this.error.set(this.requestError(error,
          'Nie udało się zapisać. Sprawdź dane, przypisania i potwierdzenia niepewnych wartości.'));
        return EMPTY;
      }),
      finalize(() => this.saving.set(false)),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe((updated) => {
      this.setDraft(updated);
      this.message.set('Zapisano zmiany.');
    });
  }

  confirmAccept(): void {
    if (this.busy() || this.form.dirty || !this.draft()?.validation.readyForAcceptance) return;
    this.openDialog('accept');
  }

  confirmReject(): void {
    if (!this.busy() && this.draft() && !this.rejected()) this.openDialog('reject');
  }

  closeDialog(): void {
    if (this.busy()) return;
    const dialog = this.confirmDialog?.nativeElement;
    if (dialog && typeof dialog.close === 'function') dialog.close();
    else dialog?.removeAttribute('open');
    this.dialogAction.set(null);
    this.pendingRemoval = null;
  }

  confirmDialogAction(): void {
    if (this.busy()) return;
    const action = this.dialogAction();
    if (action === 'accept') this.accept();
    else if (action === 'reject') this.reject();
    else if (action === 'remove-definition' && this.pendingRemoval) {
      this.definitions.removeAt(this.pendingRemoval.definition);
      this.form.markAsDirty();
      this.closeDialog();
    } else if (action === 'remove-segment' && this.pendingRemoval?.segment !== undefined) {
      const { definition, segment } = this.pendingRemoval;
      this.segments(definition).removeAt(segment);
      for (const row of this.rows(definition).controls) row.controls.executions.removeAt(segment);
      this.form.markAsDirty();
      this.closeDialog();
    }
  }

  accept(): void {
    if (this.busy() || this.form.dirty || !this.draft()?.validation.readyForAcceptance || this.rejected()) return;
    this.error.set(null);
    this.accepting.set(true);
    this.api.accept(this.importId).pipe(
      take(1),
      catchError((error: unknown) => {
        this.error.set(this.requestError(error, 'Nie udało się zaakceptować importu. Sprawdź dane i spróbuj ponownie.'));
        return EMPTY;
      }),
      finalize(() => {
        this.accepting.set(false);
        this.closeDialog();
      }),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe((result) => void this.router.navigate(['/trainings', result.trainingId]));
  }

  reject(): void {
    if (this.busy() || !this.draft() || this.rejected()) return;
    this.error.set(null);
    this.rejecting.set(true);
    this.api.reject(this.importId).pipe(
      take(1),
      catchError((error: unknown) => {
        this.error.set(this.requestError(error, 'Nie udało się odrzucić importu. Spróbuj ponownie.'));
        return EMPTY;
      }),
      finalize(() => {
        this.rejecting.set(false);
        this.closeDialog();
      }),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe(() => {
      this.rejected.set(true);
      this.message.set('Import został odrzucony. Trening nie został utworzony.');
    });
  }

  issueText(code: string): string {
    const messages: Record<string, string> = {
      SEGMENT_DISTANCE_UNRESOLVED: 'Uzupełnij dodatni dystans odcinka.',
      SEGMENT_DISTANCE_REVIEW_REQUIRED: 'Sprawdź dane odcinka.',
      RECOGNITION_REVIEW_REQUIRED: 'Potwierdź lub popraw niepewne dane.',
      NO_DEFINITIONS: 'Dodaj co najmniej jeden wariant.',
      NO_SEGMENTS: 'Każdy wariant musi zawierać odcinek.',
      SPIKE_USAGE_UNRESOLVED: 'Potwierdź użycie kolców.',
      ATHLETE_UNRESOLVED: 'Przypisz zawodnika lub pomiń wiersz.',
      ATHLETE_OUTSIDE_GROUP: 'Przypisany zawodnik nie należy już do wybranej grupy.',
      EXECUTIONS_INCOMPLETE: 'Uzupełnij dane wykonania dla każdego odcinka.',
    };
    return messages[code] ?? 'Sprawdź wskazane dane importu.';
  }

  issueLocation(path: string): string {
    const definition = /^definitions\/(\d+)(?:\/segments\/(\d+))?/.exec(path);
    if (definition) {
      const found = this.draft()?.definitions.find((item) => item.id === Number(definition[1]));
      return `Wariant ${found?.sequenceNumber ?? '?'}${definition[2] ? `, odcinek ${definition[2]}` : ''}`;
    }
    const row = /^athleteRows\/(\d+)(?:\/executions\/(\d+))?/.exec(path);
    if (row) {
      const found = this.draft()?.definitions.flatMap((item) => item.athleteRows)
        .find((item) => item.id === Number(row[1]));
      return `Zawodnik ${found?.recognizedName ?? '?'}${row[2] ? `, odcinek ${row[2]}` : ''}`;
    }
    return 'Trening';
  }

  private openDialog(action: DialogAction): void {
    this.dialogAction.set(action);
    const dialog = this.confirmDialog?.nativeElement;
    if (dialog && typeof dialog.showModal === 'function') dialog.showModal();
    else dialog?.setAttribute('open', '');
  }

  private move<T extends SegmentForm | DefinitionForm | ExecutionForm>(array: FormArray<T>, index: number, offset: number): void {
    const target = index + offset;
    if (this.busy() || target < 0 || target >= array.length) return;
    const controls = [...array.controls];
    const [control] = controls.splice(index, 1);
    controls.splice(target, 0, control);
    array.controls.splice(0, array.length, ...controls);
    array.updateValueAndValidity();
    this.form.markAsDirty();
  }

  private setDraft(draft: TrainingImportDraft): void {
    const previousControls = this.initialized ? this.resettableControls() : [];
    this.draft.set(draft);
    this.definitions.clear();
    for (const definition of draft.definitions) this.definitions.push(this.definitionForm(definition));
    this.form.controls.name.setValue(draft.name);
    this.form.controls.trainingDate.setValue(draft.trainingDate);
    this.form.controls.recognitionReviewed.setValue(draft.recognitionReviewed);
    const controls = this.resettableControls();
    if (!this.initialized) {
      for (const control of controls) this.initialValues.set(control, control.value);
      this.initialized = true;
    } else {
      controls.forEach((control, index) => {
        const previous = previousControls[index];
        if (previous && this.initialValues.has(previous)) this.initialValues.set(control, this.initialValues.get(previous));
      });
    }
    this.form.markAsPristine();
  }

  private resettableControls(): AbstractControl[] {
    return [
      this.form.controls.name,
      this.form.controls.trainingDate,
      ...this.definitions.controls.flatMap((definition) => [
        definition.controls.name,
        ...definition.controls.segments.controls.flatMap((segment) => [
          segment.controls.distanceM, segment.controls.restAfterSeconds,
        ]),
        ...definition.controls.athleteRows.controls.flatMap((row) => [
          row.controls.athleteSelection, row.controls.athleteId, row.controls.matchStatus,
          ...row.controls.executions.controls.flatMap((execution) => [
            execution.controls.timeInput, execution.controls.status,
            execution.controls.measuredResult, execution.controls.usesSpikes,
          ]),
        ]),
      ]),
    ];
  }

  private definitionForm(definition?: ImportDefinition): DefinitionForm {
    const segments = definition?.segments ?? [];
    return new FormGroup({
      id: new FormControl(definition?.id ?? null),
      originalSequence: new FormControl(definition?.sequenceNumber ?? 0, { nonNullable: true }),
      name: new FormControl(definition?.name ?? '', { nonNullable: true, validators: [nonBlank, Validators.maxLength(255)] }),
      segments: new FormArray(segments.length ? segments.map((segment) => this.segmentForm(segment)) : [this.segmentForm()]),
      athleteRows: new FormArray((definition?.athleteRows ?? []).map((row) => new FormGroup({
        id: new FormControl<number | null>(row.id),
        recognizedName: new FormControl(row.recognizedName, { nonNullable: true }),
        metadata: new FormControl(row.recognitionMetadata, { nonNullable: true }),
        matchStatus: new FormControl(row.matchStatus, { nonNullable: true }),
        athleteId: new FormControl(row.athlete?.id ?? null),
        athleteSelection: new FormControl(row.athlete ? String(row.athlete.id) : '', { nonNullable: true }),
        executions: new FormArray(segments.map((segment) =>
          this.executionForm(row.executions.find((execution) => execution.segmentId === segment.id)))),
      }))),
    });
  }

  private segmentForm(segment?: ImportSegment): SegmentForm {
    return new FormGroup({
      id: new FormControl(segment?.id ?? null),
      originalSequence: new FormControl(segment?.sequenceNumber ?? 0, { nonNullable: true }),
      metadata: new FormControl(segment?.recognitionMetadata ?? {}, { nonNullable: true }),
      distanceM: new FormControl(segment?.distanceM ?? null, { validators: [Validators.min(1), Validators.max(2147483647), integerPattern] }),
      restAfterSeconds: new FormControl(segment?.restAfterSeconds ?? null, { validators: [Validators.min(0), Validators.max(2147483647), integerPattern] }),
      recognitionReviewed: new FormControl(segment?.recognitionReviewed ?? false, { nonNullable: true }),
    });
  }

  private executionForm(execution?: ImportExecution, initialSpikes: boolean | null = null): ExecutionForm {
    const value = execution?.measuredResult;
    const display = value != null
      ? formatResult(value, Math.round(value * 1000) % 10 === 0 ? 2 : 3)
      : execution?.status === 'COMPLETED_WITHOUT_RESULT' ? 'X'
        : execution?.status === 'NOT_COMPLETED' ? '-' : '';
    return new FormGroup({
      id: new FormControl(execution?.id ?? null),
      recognizedText: new FormControl(execution?.recognizedText ?? null),
      metadata: new FormControl(execution?.recognitionMetadata ?? {}, { nonNullable: true }),
      status: new FormControl<ImportExecutionStatus>(execution?.status ?? 'PENDING', { nonNullable: true }),
      measuredResult: new FormControl(execution?.measuredResult ?? null, { validators: [Validators.min(0), Validators.max(999999999.999)] }),
      timeInput: new FormControl(display, { nonNullable: true, validators: [timeValidator] }),
      usesSpikes: new FormControl(execution ? execution.usesSpikes : initialSpikes),
      recognitionReviewed: new FormControl(execution?.recognitionReviewed ?? false, { nonNullable: true }),
    });
  }

  private toRequest(): TrainingImportDraftUpdate {
    const value = this.form.getRawValue();
    return {
      name: value.name.trim(),
      trainingDate: value.trainingDate,
      recognitionReviewed: value.recognitionReviewed,
      definitions: value.definitions.map((definition, definitionIndex) => {
        const sequences = this.sequenceNumbers(definition.segments.map((segment) => segment.originalSequence));
        return {
        id: definition.id,
        name: definition.name.trim(),
        sequenceNumber: this.sequenceNumbers(value.definitions.map((item) => item.originalSequence))[definitionIndex],
        segments: definition.segments.map((segment, segmentIndex) => ({
          id: segment.id,
          sequenceNumber: sequences[segmentIndex],
          distanceM: segment.distanceM,
          restAfterSeconds: segment.restAfterSeconds,
          recognitionReviewed: segment.recognitionReviewed,
        })),
        athleteRows: definition.athleteRows.map((row) => ({
          id: row.id,
          recognizedName: row.recognizedName,
          athleteId: row.matchStatus === 'MATCHED' ? row.athleteId : null,
          matchStatus: row.matchStatus,
          executions: row.executions.map((execution, index) => ({
            id: execution.id,
            segmentSequenceNumber: sequences[index],
            status: execution.status,
            measuredResult: execution.measuredResult,
            usesSpikes: execution.usesSpikes,
            recognitionReviewed: execution.recognitionReviewed,
          })),
        })),
        };
      }),
    };
  }

  private sequenceNumbers(original: number[]): number[] {
    let previous = 0;
    const result: number[] = [];
    for (const sequence of original) {
      const current = sequence || previous + 1;
      if (current <= previous || current > 2147483647) return original.map((_, index) => index + 1);
      result.push(current);
      previous = current;
    }
    return result;
  }

  private requestError(error: unknown, fallback: string): string {
    if (error instanceof HttpErrorResponse && error.status === 401) return 'Sesja wygasła lub użytkownik nie jest zalogowany.';
    if (error instanceof HttpErrorResponse && error.status === 403) return 'Brak dostępu do importu.';
    if (error instanceof HttpErrorResponse && error.status === 404) return 'Nie znaleziono importu lub brak dostępu.';
    if (error instanceof HttpErrorResponse && error.status === 409) return 'Stan importu uległ zmianie. Wróć do listy i odśwież dane.';
    return fallback;
  }
}

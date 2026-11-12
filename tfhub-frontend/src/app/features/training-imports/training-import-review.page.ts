import { Component, DestroyRef, ElementRef, OnInit, ViewChild, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormArray, FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { EMPTY, catchError, finalize, forkJoin, take } from 'rxjs';
import {
  ImportCandidate, ImportDefinition, ImportExecution, ImportExecutionStatus, ImportMatchStatus,
  ImportSegment, TrainingImportDraft, TrainingImportDraftUpdate,
} from './training-import.model';
import { TrainingImportsApiService } from './training-imports-api.service';

type SegmentForm = FormGroup<{
  distanceM: FormControl<number | null>;
  restAfterSeconds: FormControl<number | null>;
  recognitionReviewed: FormControl<boolean>;
}>;
type ExecutionForm = FormGroup<{
  status: FormControl<ImportExecutionStatus>;
  measuredResult: FormControl<number | null>;
  usesSpikes: FormControl<boolean | null>;
  recognitionReviewed: FormControl<boolean>;
}>;
type RowForm = FormGroup<{
  matchStatus: FormControl<ImportMatchStatus>;
  athleteId: FormControl<number | null>;
  executions: FormArray<ExecutionForm>;
}>;
type DefinitionForm = FormGroup<{
  name: FormControl<string>;
  segments: FormArray<SegmentForm>;
  athleteRows: FormArray<RowForm>;
}>;

const integerPattern = Validators.pattern(/^\d+$/);

@Component({
  selector: 'app-training-import-review-page',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './training-import-review.page.html',
  styleUrl: './training-import-review.page.scss',
})
export class TrainingImportReviewPage implements OnInit {
  @ViewChild('acceptDialog') private acceptDialog?: ElementRef<HTMLDialogElement>;

  readonly draft = signal<TrainingImportDraft | null>(null);
  readonly candidates = signal<ImportCandidate[]>([]);
  readonly loading = signal(true);
  readonly saving = signal(false);
  readonly accepting = signal(false);
  readonly imageError = signal(false);
  readonly error = signal<string | null>(null);
  readonly message = signal<string | null>(null);
  readonly form = new FormGroup({
    name: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(255)] }),
    trainingDate: new FormControl('', { nonNullable: true, validators: Validators.required }),
    recognitionReviewed: new FormControl(false, { nonNullable: true }),
    definitions: new FormArray<DefinitionForm>([]),
  });

  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly api = inject(TrainingImportsApiService);
  private readonly destroyRef = inject(DestroyRef);
  private importId = 0;

  get definitions(): FormArray<DefinitionForm> {
    return this.form.controls.definitions;
  }

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
      catchError(() => {
        this.error.set('Nie udało się pobrać importu lub brak dostępu.');
        return EMPTY;
      }),
      finalize(() => this.loading.set(false)),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe(({ draft, candidates }) => {
      if (draft.status !== 'REVIEW_REQUIRED') {
        this.error.set('Ten import nie jest dostępny do weryfikacji.');
        return;
      }
      this.candidates.set(candidates);
      this.setDraft(draft);
    });
  }

  imageUrl(): string {
    return this.api.sourceImageUrl(this.importId);
  }

  segments(definition: number): FormArray<SegmentForm> {
    return this.definitions.at(definition).controls.segments;
  }

  rows(definition: number): FormArray<RowForm> {
    return this.definitions.at(definition).controls.athleteRows;
  }

  executions(definition: number, row: number): FormArray<ExecutionForm> {
    return this.rows(definition).at(row).controls.executions;
  }

  isUncertain(metadata: Record<string, unknown>): boolean {
    return metadata['uncertain'] === true;
  }

  originalDistance(segment: ImportSegment): string | null {
    const fields = segment.recognitionMetadata['fields'];
    if (!fields || typeof fields !== 'object' || !('distanceM' in fields)) return null;
    const distance = fields.distanceM;
    if (!distance || typeof distance !== 'object' || !('originalText' in distance)) return null;
    return typeof distance.originalText === 'string' ? distance.originalText : null;
  }

  save(): void {
    const draft = this.draft();
    if (!draft || this.saving() || this.accepting()) return;
    this.message.set(null);
    this.error.set(null);
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.error.set('Sprawdź dane formularza. Dystans musi być dodatnią liczbą całkowitą.');
      return;
    }
    const request = this.toRequest(draft);
    this.saving.set(true);
    this.api.updateDraft(this.importId, request).pipe(
      take(1),
      catchError(() => {
        this.error.set('Nie udało się zapisać. Sprawdź dane, przypisania i potwierdzenia niepewnych wartości.');
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
    if (this.saving() || this.accepting() || this.form.dirty || !this.draft()?.validation.readyForAcceptance) return;
    this.acceptDialog?.nativeElement.showModal();
  }

  closeDialog(): void {
    if (!this.accepting()) this.acceptDialog?.nativeElement.close();
  }

  accept(): void {
    if (this.saving() || this.accepting() || this.form.dirty || !this.draft()?.validation.readyForAcceptance) return;
    this.error.set(null);
    this.accepting.set(true);
    this.api.accept(this.importId).pipe(
      take(1),
      catchError(() => {
        this.error.set('Nie udało się zaakceptować importu. Sprawdź dane i spróbuj ponownie.');
        return EMPTY;
      }),
      finalize(() => {
        this.accepting.set(false);
        this.acceptDialog?.nativeElement.close();
      }),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe((result) => void this.router.navigate(['/trainings', result.trainingId]));
  }

  private setDraft(draft: TrainingImportDraft): void {
    this.draft.set(draft);
    this.definitions.clear();
    for (const definition of draft.definitions) this.definitions.push(this.definitionForm(definition));
    this.form.controls.name.setValue(draft.name);
    this.form.controls.trainingDate.setValue(draft.trainingDate);
    this.form.controls.recognitionReviewed.setValue(draft.recognitionReviewed);
    this.form.markAsPristine();
  }

  private definitionForm(definition: ImportDefinition): DefinitionForm {
    return new FormGroup({
      name: new FormControl(definition.name, { nonNullable: true, validators: [Validators.required, Validators.maxLength(255)] }),
      segments: new FormArray(definition.segments.map((segment) => new FormGroup({
        distanceM: new FormControl(segment.distanceM, { validators: [Validators.min(1), Validators.max(2147483647), integerPattern] }),
        restAfterSeconds: new FormControl(segment.restAfterSeconds, { validators: [Validators.min(0), Validators.max(2147483647), integerPattern] }),
        recognitionReviewed: new FormControl(segment.recognitionReviewed, { nonNullable: true }),
      }))),
      athleteRows: new FormArray(definition.athleteRows.map((row) => new FormGroup({
        matchStatus: new FormControl(row.matchStatus, { nonNullable: true }),
        athleteId: new FormControl(row.athlete?.id ?? null),
        executions: new FormArray(row.executions.map((execution) => this.executionForm(execution))),
      }))),
    });
  }

  private executionForm(execution: ImportExecution): ExecutionForm {
    return new FormGroup({
      status: new FormControl(execution.status, { nonNullable: true }),
      measuredResult: new FormControl(execution.measuredResult, { validators: [Validators.min(0), Validators.max(999999999.999)] }),
      usesSpikes: new FormControl(execution.usesSpikes),
      recognitionReviewed: new FormControl(execution.recognitionReviewed, { nonNullable: true }),
    });
  }

  private toRequest(draft: TrainingImportDraft): TrainingImportDraftUpdate {
    const value = this.form.getRawValue();
    return {
      name: value.name.trim(),
      trainingDate: value.trainingDate,
      recognitionReviewed: value.recognitionReviewed,
      definitions: draft.definitions.map((definition, definitionIndex) => {
        const definitionValue = value.definitions[definitionIndex];
        return {
          id: definition.id,
          name: definitionValue.name.trim(),
          sequenceNumber: definition.sequenceNumber,
          segments: definition.segments.map((segment, segmentIndex) => ({
            id: segment.id,
            sequenceNumber: segment.sequenceNumber,
            ...definitionValue.segments[segmentIndex],
          })),
          athleteRows: definition.athleteRows.map((row, rowIndex) => {
            const rowValue = definitionValue.athleteRows[rowIndex];
            return {
              id: row.id,
              recognizedName: row.recognizedName,
              athleteId: rowValue.matchStatus === 'MATCHED' ? rowValue.athleteId : null,
              matchStatus: rowValue.matchStatus,
              executions: row.executions.map((execution, executionIndex) => ({
                id: execution.id,
                segmentSequenceNumber: execution.segmentSequenceNumber,
                ...rowValue.executions[executionIndex],
              })),
            };
          }),
        };
      }),
    };
  }
}

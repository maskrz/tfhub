import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { AbstractControl, FormArray, FormControl, FormGroup, ReactiveFormsModule, ValidatorFn, Validators } from '@angular/forms';
import { KeyValuePipe } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { EMPTY, catchError, finalize, take } from 'rxjs';
import {
  EditTrainingDefinitionRequest,
  EditTrainingRequest,
  TrainingDetails,
  UpdateTrainingResponse,
} from './training.model';
import { TrainingsApiService } from './trainings-api.service';

type SegmentForm = FormGroup<{
  id: FormControl<number | null>;
  distanceM: FormControl<number | null>;
  restAfterSeconds: FormControl<number | null>;
}>;

type DefinitionForm = FormGroup<{
  id: FormControl<number | null>;
  name: FormControl<string>;
  segments: FormArray<SegmentForm>;
}>;

const trimmedRequired: ValidatorFn = (control) =>
  typeof control.value === 'string' && control.value.trim().length === 0
    ? { required: true }
    : null;

const validDate: ValidatorFn = (control) => {
  const value = control.value;
  if (typeof value !== 'string' || value === '') return null;
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) return { date: true };
  const [year, month, day] = value.split('-').map(Number);
  const date = new Date(Date.UTC(year, month - 1, day));
  return date.getUTCFullYear() === year && date.getUTCMonth() === month - 1 &&
    date.getUTCDate() === day ? null : { date: true };
};

function createSegmentForm(
  id: number | null = null,
  distanceM: number | null = null,
  restAfterSeconds: number | null = null,
): SegmentForm {
  return new FormGroup({
    id: new FormControl(id),
    distanceM: new FormControl(distanceM, {
      validators: [Validators.required, Validators.min(1), Validators.max(2147483647), Validators.pattern(/^\d+$/)],
    }),
    restAfterSeconds: new FormControl(restAfterSeconds, {
      validators: [Validators.min(0), Validators.max(2147483647), Validators.pattern(/^\d+$/)],
    }),
  });
}

function createDefinitionForm(
  id: number | null = null,
  name = '',
  segments: SegmentForm[] = [createSegmentForm()],
): DefinitionForm {
  return new FormGroup({
    id: new FormControl(id),
    name: new FormControl(name, {
      nonNullable: true,
      validators: [trimmedRequired, Validators.maxLength(255)],
    }),
    segments: new FormArray(segments, { validators: [Validators.minLength(1)] }),
  });
}

@Component({
  imports: [KeyValuePipe, ReactiveFormsModule, RouterLink],
  selector: 'app-training-edit-page',
  styleUrl: './training-create.page.scss',
  templateUrl: './training-edit.page.html',
})
export class TrainingEditPage implements OnInit {
  readonly training = signal<TrainingDetails | null>(null);
  readonly loading = signal(true);
  readonly submitting = signal(false);
  readonly attemptedSubmit = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly loadError = signal<string | null>(null);
  readonly serverFieldErrors = signal<Record<string, string>>({});
  readonly form = new FormGroup({
    name: new FormControl('', {
      nonNullable: true,
      validators: [trimmedRequired, Validators.maxLength(255)],
    }),
    trainingDate: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, validDate],
    }),
    definitions: new FormArray<DefinitionForm>([], {
      validators: [Validators.minLength(1)],
    }),
  });

  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly trainingsApi = inject(TrainingsApiService);
  private readonly destroyRef = inject(DestroyRef);
  private trainingId = 0;

  get definitions(): FormArray<DefinitionForm> {
    return this.form.controls.definitions;
  }

  ngOnInit(): void {
    const trainingId = Number(this.route.snapshot.paramMap.get('id'));
    if (!Number.isSafeInteger(trainingId) || trainingId <= 0) {
      this.loading.set(false);
      this.loadError.set('Nieprawidłowy identyfikator treningu.');
      return;
    }
    this.trainingId = trainingId;
    this.loadTraining(trainingId);
  }

  addDefinition(): void {
    this.definitions.push(createDefinitionForm());
  }

  removeDefinition(index: number): void {
    if (this.definitions.length <= 1) return;
    this.definitions.removeAt(index);
  }

  moveDefinition(index: number, offset: number): void {
    this.moveControl(this.definitions, index, offset);
  }

  addSegment(definitionIndex: number): void {
    this.definitions.at(definitionIndex).controls.segments.push(createSegmentForm());
  }

  removeSegment(definitionIndex: number, segmentIndex: number): void {
    const definition = this.definitions.at(definitionIndex);
    if (definition.controls.segments.length <= 1) return;
    definition.controls.segments.removeAt(segmentIndex);
  }

  moveSegment(definitionIndex: number, segmentIndex: number, offset: number): void {
    this.moveControl(this.definitions.at(definitionIndex).controls.segments, segmentIndex, offset);
  }

  fieldError(control: AbstractControl, required: string, invalid: string): string | null {
    if (!this.attemptedSubmit() || control.valid) return null;
    if (control.hasError('required')) return required;
    if (control.hasError('min') || control.hasError('max') || control.hasError('pattern')) return invalid;
    if (control.hasError('date')) return invalid;
    return null;
  }

  submit(): void {
    if (this.submitting() || !this.training()) return;
    this.attemptedSubmit.set(true);
    this.errorMessage.set(null);
    this.serverFieldErrors.set({});
    this.form.markAllAsTouched();
    this.form.updateValueAndValidity();
    if (this.form.invalid) return;

    this.submitting.set(true);
    this.trainingsApi.update(this.trainingId, this.toRequest()).pipe(
      take(1),
      catchError((error: unknown) => {
        this.handleUpdateError(error);
        return EMPTY;
      }),
      finalize(() => this.submitting.set(false)),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe(() => {
      void this.router.navigate(['/trainings']);
    });
  }

  retry(): void {
    if (this.trainingId > 0) this.loadTraining(this.trainingId);
  }

  private loadTraining(id: number): void {
    this.loading.set(true);
    this.loadError.set(null);
    this.trainingsApi.getById(id).pipe(
      take(1),
      catchError((error: unknown) => {
        this.loadError.set(this.getLoadError(error));
        return EMPTY;
      }),
      finalize(() => this.loading.set(false)),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe((training) => {
      if (!training.active) {
        this.loadError.set('Nie można edytować nieaktywnego treningu.');
        return;
      }
      this.training.set(training);
      this.populateForm(training);
    });
  }

  private populateForm(training: TrainingDetails): void {
    this.form.controls.name.setValue(training.name);
    this.form.controls.trainingDate.setValue(training.trainingDate);
    this.definitions.clear();
    for (const definition of training.definitions) {
      const segments = definition.segments.map((segment) => createSegmentForm(
        segment.id,
        segment.distanceM,
        segment.restAfterSeconds,
      ));
      this.definitions.push(createDefinitionForm(definition.id, definition.name, segments));
    }
    this.definitions.updateValueAndValidity();
  }

  private moveControl<T extends AbstractControl>(array: FormArray<T>, index: number, offset: number): void {
    const target = index + offset;
    if (target < 0 || target >= array.length) return;
    const controls = [...array.controls];
    const [control] = controls.splice(index, 1);
    controls.splice(target, 0, control);
    array.controls.splice(0, array.length, ...controls);
    array.updateValueAndValidity();
  }

  private toRequest(): EditTrainingRequest {
    const value = this.form.getRawValue();
    const definitions: EditTrainingDefinitionRequest[] = value.definitions.map((definition, definitionIndex) => ({
      id: definition.id,
      name: definition.name.trim(),
      sequenceNumber: definitionIndex + 1,
      segments: definition.segments.map((segment, segmentIndex) => ({
        id: segment.id,
        sequenceNumber: segmentIndex + 1,
        distanceM: segment.distanceM!,
        restAfterSeconds: segment.restAfterSeconds,
      })),
    }));
    return {
      name: value.name.trim(),
      trainingDate: value.trainingDate,
      definitions,
    };
  }

  private handleUpdateError(error: unknown): void {
    if (!(error instanceof HttpErrorResponse)) {
      this.errorMessage.set('Wystąpił nieoczekiwany błąd. Wprowadzone zmiany nie zostały utracone.');
      return;
    }
    const body = error.error as { detail?: string; title?: string; errors?: Record<string, unknown> } | null;
    if (error.status === 401) {
      this.errorMessage.set('Sesja wygasła lub użytkownik nie jest zalogowany.');
    } else if (error.status === 403) {
      this.errorMessage.set('Brak dostępu do edycji tego treningu.');
    } else if (error.status === 404) {
      this.errorMessage.set('Nie znaleziono treningu.');
    } else if (error.status === 409 || error.status === 400) {
      const detail = body?.detail;
      this.errorMessage.set(this.translateBackendDetail(detail) ??
        'Nie udało się zapisać zmian. Sprawdź formularz i spróbuj ponownie.');
      if (body?.errors) {
        this.serverFieldErrors.set(Object.fromEntries(
          Object.entries(body.errors).map(([field, message]) => [field, String(message)]),
        ));
      }
    } else {
      this.errorMessage.set('Nie udało się zapisać zmian. Wprowadzone dane nie zostały utracone.');
    }
  }

  private translateBackendDetail(detail: string | undefined): string | null {
    if (!detail) return null;
    if (detail.includes('recorded execution data')) {
      return 'Nie można usunąć elementu, ponieważ ma zapisany wynik.';
    }
    if (detail.includes('distance of a segment')) {
      return 'Nie można zmienić dystansu odcinka z zapisanym wynikiem.';
    }
    if (detail.includes('currently belong to the training group')) {
      return 'Nowo przypisany zawodnik musi należeć do grupy treningu.';
    }
    if (detail.includes('only one definition')) {
      return 'Zawodnik może być przypisany tylko do jednej podgrupy.';
    }
    if (detail.includes('group cannot be changed')) {
      return 'Grupa treningowa jest niezmienna.';
    }
    return null;
  }

  private getLoadError(error: unknown): string {
    if (error instanceof HttpErrorResponse && error.status === 401) {
      return 'Sesja wygasła lub użytkownik nie jest zalogowany.';
    }
    if (error instanceof HttpErrorResponse && error.status === 403) {
      return 'Brak dostępu do tego treningu.';
    }
    if (error instanceof HttpErrorResponse && error.status === 404) {
      return 'Nie znaleziono treningu.';
    }
    return 'Nie udało się pobrać danych treningu. Spróbuj ponownie.';
  }
}

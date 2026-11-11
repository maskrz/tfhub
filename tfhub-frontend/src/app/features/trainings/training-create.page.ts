import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { AbstractControl, FormArray, FormControl, FormGroup, ReactiveFormsModule, ValidatorFn, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { EMPTY, catchError, finalize, take } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { TrainingGroup, TrainingGroupsApiService } from '../training-groups/training-groups-api.service';
import {
  CreateTrainingDefinitionRequest,
  CreateTrainingRequest,
  CreateTrainingResponse,
} from './training.model';
import { TrainingsApiService } from './trainings-api.service';

type SegmentForm = FormGroup<{
  distanceM: FormControl<number | null>;
  restAfterSeconds: FormControl<number | null>;
}>;

type DefinitionForm = FormGroup<{
  name: FormControl<string>;
  segments: FormArray<SegmentForm>;
}>;

const trimmedRequired: ValidatorFn = (control) =>
  typeof control.value === 'string' && control.value.trim().length === 0
    ? { required: true }
    : null;

const trimmedMaxLength = (max: number): ValidatorFn => (control) =>
  typeof control.value === 'string' && control.value.trim().length > max
    ? { maxlength: { requiredLength: max } }
    : null;

const integer: ValidatorFn = (control) =>
  control.value !== null && !Number.isInteger(control.value) ? { integer: true } : null;

const validDate: ValidatorFn = (control) => {
  const value = control.value;
  if (typeof value !== 'string' || value === '') return null;
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) return { date: true };
  const [year, month, day] = value.split('-').map(Number);
  const date = new Date(Date.UTC(year, month - 1, day));
  return date.getUTCFullYear() === year && date.getUTCMonth() === month - 1 &&
    date.getUTCDate() === day
    ? null
    : { date: true };
};

function createSegmentForm(): SegmentForm {
  return new FormGroup({
    distanceM: new FormControl<number | null>(null, {
      validators: [Validators.required, Validators.min(1), Validators.max(2147483647), integer],
    }),
    restAfterSeconds: new FormControl<number | null>(null, {
      validators: [Validators.min(0), Validators.max(2147483647), integer],
    }),
  });
}

function createDefinitionForm(): DefinitionForm {
  return new FormGroup({
    name: new FormControl('', {
      nonNullable: true,
      validators: [trimmedRequired, trimmedMaxLength(255)],
    }),
    segments: new FormArray<SegmentForm>([createSegmentForm()], {
      validators: [Validators.minLength(1)],
    }),
  });
}

@Component({
  imports: [ReactiveFormsModule, RouterLink],
  selector: 'app-training-create-page',
  styleUrl: './training-create.page.scss',
  templateUrl: './training-create.page.html',
})
export class TrainingCreatePage implements OnInit {
  readonly groups = signal<TrainingGroup[]>([]);
  readonly groupsLoading = signal(false);
  readonly submitting = signal(false);
  readonly attemptedSubmit = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly groupError = signal<string | null>(null);
  readonly form = new FormGroup({
    name: new FormControl('', {
      nonNullable: true,
      validators: [trimmedRequired, trimmedMaxLength(255)],
    }),
    trainingDate: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, validDate],
    }),
    trainingGroupId: new FormControl<number | null>(null, Validators.required),
    definitions: new FormArray<DefinitionForm>([createDefinitionForm()], {
      validators: [Validators.minLength(1)],
    }),
  });

  readonly auth = inject(AuthService);
  private readonly groupsApi = inject(TrainingGroupsApiService);
  private readonly trainingsApi = inject(TrainingsApiService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  get definitions(): FormArray<DefinitionForm> {
    return this.form.controls.definitions;
  }

  ngOnInit(): void {
    this.loadGroups();
  }

  onGroupChange(event: Event): void {
    const select = event.target;
    if (!(select instanceof HTMLSelectElement)) return;
    const groupId = select.value === '' ? null : Number(select.value);
    if (groupId !== null && !this.groups().some((group) => group.id === groupId)) return;

    this.form.controls.trainingGroupId.setValue(groupId);
    this.groupError.set(null);
  }

  addDefinition(): void {
    this.definitions.push(createDefinitionForm());
    this.definitions.updateValueAndValidity();
  }

  removeDefinition(index: number): void {
    if (this.definitions.length <= 1) return;
    this.definitions.removeAt(index);
    this.definitions.updateValueAndValidity();
  }

  moveDefinition(index: number, offset: number): void {
    const target = index + offset;
    if (target < 0 || target >= this.definitions.length) return;
    const definition = this.definitions.at(index);
    this.definitions.removeAt(index, { emitEvent: false });
    this.definitions.insert(target, definition, { emitEvent: false });
    this.definitions.updateValueAndValidity();
  }

  addSegment(definitionIndex: number): void {
    this.definitions.at(definitionIndex).controls.segments.push(createSegmentForm());
  }

  removeSegment(definitionIndex: number, segmentIndex: number): void {
    const segments = this.definitions.at(definitionIndex).controls.segments;
    if (segments.length > 1) segments.removeAt(segmentIndex);
  }

  moveSegment(definitionIndex: number, segmentIndex: number, offset: number): void {
    const segments = this.definitions.at(definitionIndex).controls.segments;
    const target = segmentIndex + offset;
    if (target < 0 || target >= segments.length) return;
    const segment = segments.at(segmentIndex);
    segments.removeAt(segmentIndex, { emitEvent: false });
    segments.insert(target, segment, { emitEvent: false });
    segments.updateValueAndValidity();
  }

  fieldError(
    control: AbstractControl,
    requiredMessage: string,
    invalidMessage: string,
  ): string | null {
    if (!this.attemptedSubmit() || control.valid) return null;
    if (control.hasError('required')) return requiredMessage;
    if (control.hasError('min') || control.hasError('max') || control.hasError('integer')) {
      return invalidMessage;
    }
    if (control.hasError('date')) return invalidMessage;
    if (control.hasError('maxlength')) return 'Nazwa może mieć maksymalnie 255 znaków.';
    return null;
  }

  get trainingGroupId(): number | null {
    return this.form.controls.trainingGroupId.value;
  }

  submit(): void {
    if (this.submitting()) return;
    this.attemptedSubmit.set(true);
    this.errorMessage.set(null);
    this.form.markAllAsTouched();
    this.definitions.updateValueAndValidity();
    if (this.form.invalid) return;

    this.submitting.set(true);
    this.trainingsApi.create(this.toRequest()).pipe(
      take(1),
      catchError((error: unknown) => {
        this.errorMessage.set(this.getErrorMessage(error));
        return EMPTY;
      }),
      finalize(() => this.submitting.set(false)),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe((response: CreateTrainingResponse) => {
      void this.router.navigate(['/trainings', response.id]);
    });
  }

  private loadGroups(): void {
    this.groupsLoading.set(true);
    this.errorMessage.set(null);
    this.groupsApi.getAccessible().pipe(
      take(1),
      catchError((error: unknown) => {
        this.errorMessage.set(this.getResourceError(error, 'dostępnych grup treningowych'));
        return EMPTY;
      }),
      finalize(() => this.groupsLoading.set(false)),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe((groups) => {
      this.groups.set(groups);
      if (groups.length === 0) this.groupError.set('Nie masz dostępnych grup treningowych.');
    });
  }

  private toRequest(): CreateTrainingRequest {
    const value = this.form.getRawValue();
    const definitions: CreateTrainingDefinitionRequest[] = value.definitions.map((definition, index) => ({
      name: definition.name.trim(),
      sequenceNumber: index + 1,
      segments: definition.segments.map((segment, segmentIndex) => ({
        sequenceNumber: segmentIndex + 1,
        distanceM: segment.distanceM!,
        restAfterSeconds: segment.restAfterSeconds,
      })),
    }));
    return {
      name: value.name.trim(),
      trainingDate: value.trainingDate,
      trainingGroupId: value.trainingGroupId!,
      definitions,
    };
  }

  private getResourceError(error: unknown, resource: string): string {
    if (error instanceof HttpErrorResponse && error.status === 401) {
      return 'Sesja wygasła lub użytkownik nie jest zalogowany.';
    }
    if (error instanceof HttpErrorResponse && error.status === 403) {
      return 'Brak dostępu do ' + resource + '.';
    }
    return `Nie udało się pobrać ${resource}. Spróbuj ponownie.`;
  }

  private getErrorMessage(error: unknown): string {
    if (error instanceof HttpErrorResponse && error.status === 401) {
      return 'Sesja wygasła lub użytkownik nie jest zalogowany.';
    }
    if (error instanceof HttpErrorResponse && error.status === 403) {
      return 'Brak dostępu do utworzenia treningu.';
    }
    if (error instanceof HttpErrorResponse && error.status === 400) {
      return 'Sprawdź poprawność danych treningu i spróbuj ponownie.';
    }
    return 'Nie udało się utworzyć treningu. Wprowadzone dane nie zostały utracone.';
  }
}

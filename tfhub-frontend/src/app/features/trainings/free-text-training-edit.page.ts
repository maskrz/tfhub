import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { AbstractControl, FormArray, FormControl, FormGroup, ReactiveFormsModule, ValidatorFn, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { EMPTY, catchError, finalize, take } from 'rxjs';
import { TrainingDetails } from './training.model';
import { TrainingsApiService } from './trainings-api.service';

type TrainingSectionForm = FormGroup<{
  id: FormControl<number | null>;
  title: FormControl<string>;
  content: FormControl<string>;
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

function createSectionForm(
  id: number | null = null,
  title = '',
  content = '',
): TrainingSectionForm {
  return new FormGroup({
    id: new FormControl(id),
    title: new FormControl(title, { nonNullable: true, validators: [trimmedRequired] }),
    content: new FormControl(content, { nonNullable: true, validators: [trimmedRequired] }),
  });
}

@Component({
  imports: [ReactiveFormsModule, RouterLink],
  selector: 'app-free-text-training-edit-page',
  styleUrl: './training-create.page.scss',
  templateUrl: './free-text-training-edit.page.html',
})
export class FreeTextTrainingEditPage implements OnInit {
  readonly training = signal<TrainingDetails | null>(null);
  readonly loading = signal(true);
  readonly submitting = signal(false);
  readonly attemptedSubmit = signal(false);
  readonly loadError = signal<string | null>(null);
  readonly errorMessage = signal<string | null>(null);
  readonly form = new FormGroup({
    name: new FormControl('', { nonNullable: true, validators: [trimmedRequired, Validators.maxLength(255)] }),
    trainingDate: new FormControl('', { nonNullable: true, validators: [Validators.required, validDate] }),
    sections: new FormArray<TrainingSectionForm>([], { validators: [Validators.minLength(1)] }),
  });

  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly trainingsApi = inject(TrainingsApiService);
  private readonly destroyRef = inject(DestroyRef);
  trainingId = 0;

  get sections(): FormArray<TrainingSectionForm> {
    return this.form.controls.sections;
  }

  ngOnInit(): void {
    const trainingId = Number(this.route.snapshot.paramMap.get('id'));
    if (!Number.isSafeInteger(trainingId) || trainingId <= 0) {
      this.loading.set(false);
      this.loadError.set('Nieprawidłowy identyfikator treningu.');
      return;
    }
    this.trainingId = trainingId;
    this.loadTraining();
  }

  addSection(): void {
    this.sections.push(createSectionForm());
  }

  removeSection(index: number): void {
    if (this.sections.length > 1) this.sections.removeAt(index);
  }

  moveSection(index: number, offset: number): void {
    const target = index + offset;
    if (target < 0 || target >= this.sections.length) return;
    const controls = [...this.sections.controls];
    const [section] = controls.splice(index, 1);
    controls.splice(target, 0, section);
    this.sections.controls.splice(0, this.sections.length, ...controls);
    this.sections.updateValueAndValidity();
  }

  fieldError(control: AbstractControl, required: string, invalid: string): string | null {
    if (!this.attemptedSubmit() || control.valid) return null;
    if (control.hasError('required')) return required;
    if (control.hasError('date')) return invalid;
    if (control.hasError('maxlength')) return 'Nazwa może mieć maksymalnie 255 znaków.';
    return null;
  }

  retry(): void {
    this.loadTraining();
  }

  submit(): void {
    if (this.submitting() || !this.training()) return;
    this.attemptedSubmit.set(true);
    this.errorMessage.set(null);
    this.form.markAllAsTouched();
    this.form.updateValueAndValidity();
    if (this.form.invalid) return;

    const value = this.form.getRawValue();
    this.submitting.set(true);
    this.trainingsApi.updateFreeText(this.trainingId, {
      name: value.name.trim(),
      trainingDate: value.trainingDate,
      sections: value.sections.map((section, index) => ({
        id: section.id,
        title: section.title.trim(),
        content: section.content.trim(),
        sequenceNumber: index + 1,
      })),
    }).pipe(
      take(1),
      catchError((error: unknown) => {
        this.errorMessage.set(this.getErrorMessage(error));
        return EMPTY;
      }),
      finalize(() => this.submitting.set(false)),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe(() => void this.router.navigate(['/trainings', this.trainingId]));
  }

  private loadTraining(): void {
    this.loading.set(true);
    this.loadError.set(null);
    this.trainingsApi.getById(this.trainingId).pipe(
      take(1),
      catchError((error: unknown) => {
        this.loadError.set(this.getErrorMessage(error));
        return EMPTY;
      }),
      finalize(() => this.loading.set(false)),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe((training) => {
      if (training.trainingType !== 'FREE_TEXT') {
        void this.router.navigate(['/trainings', training.id, 'edit'], { replaceUrl: true });
        return;
      }
      this.training.set(training);
      this.form.controls.name.setValue(training.name);
      this.form.controls.trainingDate.setValue(training.trainingDate);
      this.sections.clear();
      for (const section of training.sections) {
        this.sections.push(createSectionForm(section.id, section.title, section.content));
      }
      this.sections.updateValueAndValidity();
    });
  }

  private getErrorMessage(error: unknown): string {
    if (error instanceof HttpErrorResponse && error.status === 401) {
      return 'Sesja wygasła lub użytkownik nie jest zalogowany.';
    }
    if (error instanceof HttpErrorResponse && error.status === 403) {
      return 'Brak dostępu do tego treningu.';
    }
    if (error instanceof HttpErrorResponse && error.status === 404) {
      return 'Nie znaleziono treningu.';
    }
    if (error instanceof HttpErrorResponse && error.status === 400) {
      return 'Sprawdź poprawność danych treningu i spróbuj ponownie.';
    }
    return 'Nie udało się wykonać operacji na treningu. Wprowadzone dane nie zostały utracone.';
  }
}

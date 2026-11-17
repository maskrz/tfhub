import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { AbstractControl, FormArray, FormControl, FormGroup, ReactiveFormsModule, ValidatorFn, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { EMPTY, catchError, finalize, take } from 'rxjs';
import { AthleteResponse } from '../athletes/athlete.model';
import { AthletesApiService } from '../athletes/athletes-api.service';
import { MultiSelectDropdownComponent, MultiSelectOption } from '../../shared/multi-select-dropdown.component';
import { TrainingGroup, TrainingGroupsApiService } from '../training-groups/training-groups-api.service';
import { TrainingAssignmentType, TrainingDetails } from './training.model';
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
  imports: [ReactiveFormsModule, RouterLink, MultiSelectDropdownComponent],
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
  readonly assignmentError = signal<string | null>(null);
  readonly assignmentType = signal<TrainingAssignmentType>('GROUP');
  readonly groups = signal<TrainingGroup[]>([]);
  readonly athletes = signal<AthleteResponse[]>([]);
  readonly athleteOptions = () => this.athletes().map((athlete): MultiSelectOption => ({
    id: athlete.id,
    name: `${athlete.firstName} ${athlete.lastName}`,
  }));
  readonly groupsLoading = signal(true);
  readonly athletesLoading = signal(true);
  readonly form = new FormGroup({
    name: new FormControl('', { nonNullable: true, validators: [trimmedRequired, Validators.maxLength(255)] }),
    trainingDate: new FormControl('', { nonNullable: true, validators: [validDate] }),
    equipment: new FormControl('', { nonNullable: true }),
    trainingGroupId: new FormControl<number | null>(null, Validators.required),
    athleteIds: new FormControl<number[]>({ value: [], disabled: true }, {
      nonNullable: true,
      validators: [Validators.minLength(1)],
    }),
    sections: new FormArray<TrainingSectionForm>([], { validators: [Validators.minLength(1)] }),
  });

  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly trainingsApi = inject(TrainingsApiService);
  private readonly groupsApi = inject(TrainingGroupsApiService);
  private readonly athletesApi = inject(AthletesApiService);
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
    this.loadAssignmentOptions();
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

  onAssignmentTypeChange(event: Event): void {
    const select = event.target;
    if (!(select instanceof HTMLSelectElement)) return;
    if (select.value !== 'GROUP' && select.value !== 'ATHLETES') return;
    this.applyAssignmentType(select.value);
  }

  onGroupChange(event: Event): void {
    const select = event.target;
    if (!(select instanceof HTMLSelectElement)) return;
    const groupId = select.value === '' ? null : Number(select.value);
    if (groupId !== null && !this.groups().some((group) => group.id === groupId)) return;
    this.form.controls.trainingGroupId.setValue(groupId);
  }

  onAthleteIdsChange(athleteIds: number[]): void {
    this.form.controls.athleteIds.setValue(athleteIds);
    this.form.controls.athleteIds.markAsTouched();
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
      trainingDate: value.trainingDate || null,
      assignmentType: this.assignmentType(),
      trainingGroupId: this.assignmentType() === 'GROUP' ? value.trainingGroupId : null,
      athleteIds: this.assignmentType() === 'ATHLETES' ? value.athleteIds : [],
      equipment: value.equipment || null,
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
      this.form.controls.trainingDate.setValue(training.trainingDate ?? '');
      this.form.controls.equipment.setValue(training.equipment ?? '');
      if (training.status === 'COMPLETED') {
        this.form.controls.trainingDate.addValidators(Validators.required);
        this.form.controls.trainingDate.updateValueAndValidity();
      }
      this.applyAssignmentType(training.assignmentType, false);
      this.form.controls.trainingGroupId.setValue(training.trainingGroup?.id ?? null);
      this.form.controls.athleteIds.setValue(training.assignedAthletes.map((athlete) => athlete.id));
      this.sections.clear();
      for (const section of training.sections) {
        this.sections.push(createSectionForm(section.id, section.title, section.content));
      }
      this.sections.updateValueAndValidity();
    });
  }

  private loadAssignmentOptions(): void {
    this.groupsApi.getAccessible().pipe(
      take(1),
      catchError((error: unknown) => {
        this.assignmentError.set(this.getErrorMessage(error));
        return EMPTY;
      }),
      finalize(() => this.groupsLoading.set(false)),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe((groups) => this.groups.set(groups));
    this.athletesApi.getActive().pipe(
      take(1),
      catchError((error: unknown) => {
        this.assignmentError.set(this.getErrorMessage(error));
        return EMPTY;
      }),
      finalize(() => this.athletesLoading.set(false)),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe((athletes) => this.athletes.set(athletes));
  }

  private applyAssignmentType(assignmentType: TrainingAssignmentType, clearTargets = true): void {
    this.assignmentType.set(assignmentType);
    const groupControl = this.form.controls.trainingGroupId;
    const athleteControl = this.form.controls.athleteIds;
    if (clearTargets) {
      groupControl.setValue(null, { emitEvent: false });
      athleteControl.setValue([], { emitEvent: false });
    }
    if (assignmentType === 'GROUP') {
      groupControl.setValidators([Validators.required]);
      groupControl.enable({ emitEvent: false });
      athleteControl.disable({ emitEvent: false });
    } else {
      groupControl.clearValidators();
      groupControl.disable({ emitEvent: false });
      athleteControl.setValidators([Validators.minLength(1)]);
      athleteControl.enable({ emitEvent: false });
    }
    groupControl.updateValueAndValidity({ emitEvent: false });
    athleteControl.updateValueAndValidity({ emitEvent: false });
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

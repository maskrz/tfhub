import { HttpErrorResponse } from '@angular/common/http';
import { Component, EventEmitter, Input, Output, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, ValidatorFn, Validators } from '@angular/forms';
import { EMPTY, catchError, finalize, take } from 'rxjs';
import {
  AthleteFormMode,
  AthleteResponse,
  CreateAthleteRequest,
  Gender,
  UpdateAthleteRequest,
} from './athlete.model';
import { AthletesApiService } from './athletes-api.service';
import { AuthService } from '../../core/auth/auth.service';
import { TrainingGroup, TrainingGroupsApiService } from '../training-groups/training-groups-api.service';

const genders: Gender[] = ['MALE', 'FEMALE'];
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

@Component({
  imports: [ReactiveFormsModule],
  selector: 'app-athlete-form',
  templateUrl: './athlete-form.component.html',
  styleUrl: './athlete-form.component.scss',
})
export class AthleteFormComponent {
  @Input({ required: true }) mode!: AthleteFormMode;
  @Input() athlete: AthleteResponse | null = null;
  @Output() saved = new EventEmitter<AthleteResponse>();
  @Output() savingChange = new EventEmitter<boolean>();

  readonly saving = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly serverFieldErrors = signal<Record<string, string>>({});
  readonly genders = genders;
  readonly currentYear = new Date().getFullYear();
  readonly groups = signal<TrainingGroup[]>([]);
  readonly groupsLoading = signal(false);
  readonly form = new FormGroup({
    firstName: new FormControl('', {
      nonNullable: true,
      validators: [trimmedRequired, trimmedMaxLength(100)],
    }),
    lastName: new FormControl('', {
      nonNullable: true,
      validators: [trimmedRequired, trimmedMaxLength(100)],
    }),
    birthYear: new FormControl<number | null>(null, {
      validators: [
        Validators.required,
        Validators.min(1900),
        Validators.max(this.currentYear),
        integer,
      ],
    }),
    gender: new FormControl<Gender | ''>('', {
      nonNullable: true,
      validators: [Validators.required],
    }),
    trainingGroupId: new FormControl<number | null>(null),
  });

  private readonly api = inject(AthletesApiService);
  private readonly groupsApi = inject(TrainingGroupsApiService);
  readonly auth = inject(AuthService);

  ngOnInit(): void {
    if (this.mode === 'CREATE') {
      if (this.auth.currentUser()?.role === 'COACH') {
        this.form.controls.trainingGroupId.addValidators(Validators.required);
      }
      this.groupsLoading.set(true);
      this.groupsApi.getAccessible().pipe(
        take(1),
        catchError((error: unknown) => {
          this.errorMessage.set(error instanceof HttpErrorResponse && error.status === 403
            ? 'Brak dostępu do przypisania grupy zawodnikowi.'
            : 'Nie udało się pobrać dostępnych grup treningowych.');
          return EMPTY;
        }),
        finalize(() => this.groupsLoading.set(false)),
      ).subscribe((groups) => {
        this.groups.set(groups);
        if (this.auth.currentUser()?.role === 'COACH' && groups.length === 0) {
          this.errorMessage.set('Nie masz przypisanej grupy. Skontaktuj się z administratorem.');
        }
      });
    }
    if (this.mode === 'EDIT' && this.athlete) {
      this.form.setValue({
        firstName: this.athlete.firstName,
        lastName: this.athlete.lastName,
        birthYear: this.athlete.birthYear,
        gender: this.athlete.gender,
        trainingGroupId: null,
      });
    }
  }

  fieldError(field: 'firstName' | 'lastName' | 'birthYear' | 'gender' | 'trainingGroupId'): string | null {
    const control = this.form.controls[field];
    if (control.invalid && (control.touched || control.dirty)) {
      if (control.hasError('required')) {
        return field === 'gender'
          ? 'Wybierz płeć.'
          : field === 'birthYear'
            ? 'Wpisz rok urodzenia.'
            : 'To pole jest wymagane.';
      }
      if (control.hasError('maxlength')) {
        return 'Wpisz maksymalnie 100 znaków.';
      }
      if (control.hasError('min')) {
        return 'Rok urodzenia nie może być wcześniejszy niż 1900.';
      }
      if (control.hasError('max')) {
        return `Rok urodzenia nie może być późniejszy niż ${this.currentYear}.`;
      }
      if (control.hasError('integer')) {
        return 'Rok urodzenia musi być liczbą całkowitą.';
      }
      if (field === 'trainingGroupId' && control.hasError('required')) {
        return 'Wybierz grupę treningową.';
      }
    }
    return this.serverFieldErrors()[field] ?? null;
  }

  submit(): void {
    if (this.saving()) {
      return;
    }

    this.errorMessage.set(null);
    this.serverFieldErrors.set({});
    this.form.markAllAsTouched();
    const gender = this.form.controls.gender.value;
    const birthYear = this.form.controls.birthYear.value;

    if (this.form.invalid || !this.isGender(gender) || birthYear === null) {
      return;
    }

    if (this.mode === 'EDIT' && !this.athlete) {
      this.errorMessage.set('Nie można określić edytowanego zawodnika.');
      return;
    }

    const request = {
      firstName: this.form.controls.firstName.value.trim(),
      lastName: this.form.controls.lastName.value.trim(),
      birthYear,
      gender,
      ...(this.form.controls.trainingGroupId.value !== null
        ? { trainingGroupId: this.form.controls.trainingGroupId.value }
        : {}),
    };

    this.setSaving(true);
    const saveRequest =
      this.mode === 'CREATE'
        ? this.api.createAthlete(request satisfies CreateAthleteRequest)
        : this.api.updateAthlete(this.athlete!.id, request satisfies UpdateAthleteRequest);

    saveRequest
      .pipe(
        take(1),
        catchError((error: unknown) => {
          this.handleError(error);
          return EMPTY;
        }),
        finalize(() => this.setSaving(false)),
      )
      .subscribe((athlete) => this.saved.emit(athlete));
  }

  private setSaving(saving: boolean): void {
    this.saving.set(saving);
    this.savingChange.emit(saving);
  }

  private isGender(value: string): value is Gender {
    return genders.includes(value as Gender);
  }

  private handleError(error: unknown): void {
    if (!(error instanceof HttpErrorResponse)) {
      this.errorMessage.set('Wystąpił nieoczekiwany błąd. Spróbuj ponownie.');
      return;
    }

    const body = error.error as { title?: string; errors?: Record<string, unknown> } | null;
    const fieldErrors: Record<string, string> = {};
    for (const field of Object.keys(body?.errors ?? {})) {
      if (field === 'firstName' || field === 'lastName') {
        fieldErrors[field] = 'Wpisz od 1 do 100 znaków.';
      } else if (field === 'birthYear') {
        fieldErrors[field] = `Wpisz rok urodzenia od 1900 do ${this.currentYear}.`;
      } else if (field === 'gender') {
        fieldErrors[field] = 'Wybierz prawidłową płeć.';
      }
    }
    this.serverFieldErrors.set(fieldErrors);

    if (error.status === 401) {
      this.errorMessage.set('Sesja wygasła lub użytkownik nie jest zalogowany.');
    } else if (error.status === 403) {
      this.errorMessage.set(
        this.mode === 'CREATE'
          ? 'Brak dostępu do dodania zawodnika.'
          : 'Brak dostępu do edycji zawodnika.',
      );
    } else if (error.status === 409 && body?.title === 'Athlete already exists') {
      this.errorMessage.set('Zawodnik o tym imieniu, nazwisku i roku urodzenia już istnieje.');
    } else if (error.status === 400) {
      this.errorMessage.set('Sprawdź poprawność danych i spróbuj ponownie.');
    } else if (error.status === 404 && this.mode === 'EDIT') {
      this.errorMessage.set('Nie znaleziono zawodnika do edycji.');
    } else {
      this.errorMessage.set(
        this.mode === 'CREATE'
          ? 'Nie udało się dodać zawodnika. Spróbuj ponownie.'
          : 'Nie udało się zapisać zmian zawodnika. Spróbuj ponownie.',
      );
    }
  }
}

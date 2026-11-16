import { HttpErrorResponse } from '@angular/common/http';
import { Component, EventEmitter, Input, Output, inject, signal } from '@angular/core';
import { FormArray, FormControl, FormGroup, ReactiveFormsModule, ValidatorFn, Validators } from '@angular/forms';
import { EMPTY, catchError, finalize, of, switchMap, take } from 'rxjs';
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
    pzlaId: new FormControl('', {
      nonNullable: true,
      validators: [Validators.maxLength(20), Validators.pattern(/^\d*$/)],
    }),
    trainingGroupId: new FormControl<number | null>(null),
    aliases: new FormArray<FormControl<string>>([]),
  });

  private readonly api = inject(AthletesApiService);
  private readonly groupsApi = inject(TrainingGroupsApiService);
  readonly auth = inject(AuthService);

  ngOnInit(): void {
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
    if (this.mode === 'EDIT' && this.athlete) {
      for (const alias of this.athlete.aliases ?? []) {
        this.addAlias(alias);
      }
      this.form.setValue({
        firstName: this.athlete.firstName,
        lastName: this.athlete.lastName,
        birthYear: this.athlete.birthYear,
        gender: this.athlete.gender,
        pzlaId: this.athlete.pzlaId ?? '',
        trainingGroupId: this.athlete.trainingGroupId ?? null,
        aliases: this.athlete.aliases ?? [],
      });
    }
  }

  addAlias(value = ''): void {
    this.form.controls.aliases.push(new FormControl(value, {
      nonNullable: true,
      validators: [trimmedRequired, Validators.maxLength(255)],
    }));
  }

  removeAlias(index: number): void {
    this.form.controls.aliases.removeAt(index);
  }

  fieldError(field: 'firstName' | 'lastName' | 'birthYear' | 'gender' | 'trainingGroupId' | 'pzlaId'): string | null {
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
      if (field === 'pzlaId' && control.hasError('pattern')) {
        return 'Identyfikator PZLA może zawierać wyłącznie cyfry.';
      }
      if (field === 'pzlaId' && control.hasError('maxlength')) {
        return 'Identyfikator PZLA może mieć maksymalnie 20 cyfr.';
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

    const aliases = this.form.controls.aliases.getRawValue();
    const trimmedAliases = aliases.map((alias) => alias.trim());
    if (new Set(trimmedAliases).size !== trimmedAliases.length) {
      this.errorMessage.set('Ten sam alias występuje na liście więcej niż raz.');
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
      aliases,
      ...(this.form.controls.trainingGroupId.value !== null
        ? { trainingGroupId: this.form.controls.trainingGroupId.value }
        : {}),
    };

    this.setSaving(true);
    const athleteSaveRequest =
      this.mode === 'CREATE'
        ? this.api.createAthlete(request satisfies CreateAthleteRequest)
        : this.api.updateAthlete(this.athlete!.id, request satisfies UpdateAthleteRequest);
    const saveRequest = this.mode === 'EDIT' && this.auth.currentUser()?.role === 'ADMIN'
      ? athleteSaveRequest.pipe(switchMap((savedAthlete) => {
        const pzlaId = this.form.controls.pzlaId.value.trim();
        if (pzlaId === (this.athlete?.pzlaId ?? '')) return of(savedAthlete);
        return this.api.updatePzlaId(savedAthlete.id, pzlaId || null).pipe(
          catchError((error: unknown) => {
            this.handlePzlaIdError(error);
            return EMPTY;
          }),
        );
      }))
      : athleteSaveRequest;

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
      } else if (field === 'pzlaId') {
        fieldErrors[field] = 'Identyfikator PZLA może zawierać wyłącznie cyfry.';
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
    } else if (error.status === 409 && body?.title === 'Athlete alias conflict') {
      this.errorMessage.set('Alias jest już używany przez aktywnego zawodnika w tej grupie lub powtarza się na liście.');
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

  private handlePzlaIdError(error: unknown): void {
    if (error instanceof HttpErrorResponse && error.status === 409
        && (error.error as { title?: string } | null)?.title === 'PZLA athlete ID conflict') {
      this.serverFieldErrors.set({ pzlaId: 'Ten identyfikator PZLA jest już przypisany do innego zawodnika.' });
      this.errorMessage.set('Pozostałe dane zawodnika zapisano, ale identyfikator PZLA jest już używany.');
    } else {
      this.errorMessage.set('Pozostałe dane zawodnika zapisano, ale nie udało się zapisać identyfikatora PZLA.');
    }
  }
}

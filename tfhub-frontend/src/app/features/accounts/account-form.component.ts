import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, EventEmitter, Input, Output, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, ValidatorFn, Validators } from '@angular/forms';
import { EMPTY, catchError, finalize, take } from 'rxjs';
import { UserRole } from '../../core/auth/auth.model';
import { MultiSelectDropdownComponent, MultiSelectOption } from '../../shared/multi-select-dropdown.component';
import { SearchableSelectDropdownComponent } from '../../shared/searchable-select-dropdown.component';
import { AthleteResponse } from '../athletes/athlete.model';
import { AthletesApiService } from '../athletes/athletes-api.service';
import { TrainingGroup, TrainingGroupsApiService } from '../training-groups/training-groups-api.service';
import { AccountResponse, CreateAccountRequest, UpdateAccountRequest } from './account.model';
import { AccountsApiService } from './accounts-api.service';

export type AccountFormMode = 'CREATE' | 'EDIT';

const notBlank: ValidatorFn = (control) =>
  typeof control.value === 'string' && control.value.trim().length === 0 ? { required: true } : null;
const trimmedMaxLength = (max: number): ValidatorFn => (control) =>
  typeof control.value === 'string' && control.value.trim().length > max
    ? { maxlength: { requiredLength: max } }
    : null;

@Component({
  imports: [MultiSelectDropdownComponent, SearchableSelectDropdownComponent, ReactiveFormsModule],
  selector: 'app-account-form',
  templateUrl: './account-form.component.html',
  styleUrl: './account-form.component.scss',
})
export class AccountFormComponent {
  @Input({ required: true }) mode!: AccountFormMode;
  @Input() account: AccountResponse | null = null;
  @Output() saved = new EventEmitter<AccountResponse>();
  @Output() savingChange = new EventEmitter<boolean>();

  readonly saving = signal(false);
  readonly groupsLoading = signal(false);
  readonly athletesLoading = signal(false);
  readonly groupsError = signal<string | null>(null);
  readonly athletesError = signal<string | null>(null);
  readonly errorMessage = signal<string | null>(null);
  readonly serverFieldErrors = signal<Record<string, string>>({});
  readonly groups = signal<TrainingGroup[]>([]);
  readonly athletes = signal<AthleteResponse[]>([]);
  readonly roles: UserRole[] = ['ADMIN', 'COACH', 'ATHLETE'];
  readonly form = new FormGroup({
    username: new FormControl('', {
      nonNullable: true,
      validators: [notBlank, trimmedMaxLength(100)],
    }),
    password: new FormControl('', {
      nonNullable: true,
      validators: [notBlank, Validators.maxLength(1024)],
    }),
    role: new FormControl<UserRole>('ADMIN', { nonNullable: true, validators: [Validators.required] }),
    trainingGroupIds: new FormControl<number[]>([], { nonNullable: true }),
    athleteId: new FormControl<number | null>(null),
  });

  private readonly api = inject(AccountsApiService);
  private readonly groupsApi = inject(TrainingGroupsApiService);
  private readonly athletesApi = inject(AthletesApiService);
  private readonly destroyRef = inject(DestroyRef);

  ngOnInit(): void {
    if (this.mode === 'EDIT') {
      this.form.controls.password.clearValidators();
      this.form.controls.password.updateValueAndValidity({ emitEvent: false });
      if (this.account) {
        this.form.controls.username.setValue(this.account.username);
        this.form.controls.role.setValue(this.account.role, { emitEvent: false });
        this.form.controls.trainingGroupIds.setValue(this.account.trainingGroups.map((group) => group.id));
        this.form.controls.athleteId.setValue(this.account.athlete?.id ?? null);
      }
    }
    this.updateAssignmentValidators();
    this.form.controls.role.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((role) => {
        this.form.controls.trainingGroupIds.setValue([]);
        this.form.controls.athleteId.setValue(null);
        this.serverFieldErrors.set({});
        this.updateAssignmentValidators(role);
      });
    this.loadAssignmentOptions();
  }

  get groupOptions(): MultiSelectOption[] {
    return this.groups().map((group) => ({ id: group.id, name: group.name }));
  }

  get athleteOptions(): MultiSelectOption[] {
    return this.athletes().map((athlete) => ({
      id: athlete.id,
      name: `${athlete.firstName} ${athlete.lastName} (${athlete.birthYear})`,
    }));
  }

  onAthleteIdChange(id: number): void {
    this.form.controls.athleteId.setValue(id);
    this.form.controls.athleteId.markAsTouched();
  }

  roleLabel(role: UserRole): string {
    switch (role) {
      case 'ADMIN': return 'Administrator';
      case 'COACH': return 'Trener';
      case 'ATHLETE': return 'Zawodnik';
    }
  }

  onGroupIdsChange(ids: number[]): void {
    this.form.controls.trainingGroupIds.setValue(ids);
    this.form.controls.trainingGroupIds.markAsTouched();
  }

  fieldError(field: 'username' | 'password' | 'trainingGroupIds' | 'athleteId'): string | null {
    const control = this.form.controls[field];
    if (control.invalid && (control.touched || control.dirty)) {
      if (control.hasError('required')) {
        if (field === 'trainingGroupIds') return 'Wybierz co najmniej jedną grupę treningową.';
        if (field === 'athleteId') return 'Wybierz zawodnika.';
        return 'To pole jest wymagane.';
      }
      if (control.hasError('maxlength')) {
        return field === 'password'
          ? 'Hasło może mieć maksymalnie 1024 znaki.'
          : 'Nazwa użytkownika może mieć maksymalnie 100 znaków.';
      }
    }
    return this.serverFieldErrors()[field] ?? null;
  }

  submit(): void {
    if (this.saving()) return;

    this.errorMessage.set(null);
    this.serverFieldErrors.set({});
    this.form.markAllAsTouched();
    if (this.form.invalid) return;
    if (this.mode === 'EDIT' && !this.account) {
      this.errorMessage.set('Nie można określić edytowanego konta.');
      return;
    }
    const role = this.form.controls.role.value;
    if ((role === 'COACH' && this.groupsLoading()) || (role === 'ATHLETE' && this.athletesLoading())) {
      this.errorMessage.set('Poczekaj na załadowanie list przypisań.');
      return;
    }
    if ((role === 'COACH' && this.groupsError()) || (role === 'ATHLETE' && this.athletesError())) {
      this.errorMessage.set('Nie udało się pobrać wymaganych opcji przypisań. Zamknij formularz i spróbuj ponownie.');
      return;
    }

    const request = {
      username: this.form.controls.username.value.trim(),
      role,
      trainingGroupIds: role === 'COACH' ? this.form.controls.trainingGroupIds.value : [],
      athleteId: role === 'ATHLETE' ? this.form.controls.athleteId.value : null,
    };
    if (role === 'COACH' && request.trainingGroupIds.length === 0) {
      this.form.controls.trainingGroupIds.markAsTouched();
      return;
    }
    if (role === 'ATHLETE' && request.athleteId === null) {
      this.form.controls.athleteId.markAsTouched();
      return;
    }

    const saveRequest = this.mode === 'CREATE'
      ? this.api.createAccount({
          ...request,
          password: this.form.controls.password.value,
        } satisfies CreateAccountRequest)
      : this.api.updateAccount(this.account!.id, request satisfies UpdateAccountRequest);

    this.setSaving(true);
    saveRequest.pipe(
      take(1),
      catchError((error: unknown) => {
        this.handleError(error);
        return EMPTY;
      }),
      finalize(() => this.setSaving(false)),
    ).subscribe((account) => {
      this.form.controls.password.reset('');
      this.saved.emit(account);
    });
  }

  private updateAssignmentValidators(role = this.form.controls.role.value): void {
    const groupControl = this.form.controls.trainingGroupIds;
    const athleteControl = this.form.controls.athleteId;
    groupControl.setValidators(role === 'COACH' ? Validators.required : null);
    athleteControl.setValidators(role === 'ATHLETE' ? Validators.required : null);
    groupControl.updateValueAndValidity({ emitEvent: false });
    athleteControl.updateValueAndValidity({ emitEvent: false });
  }

  private loadAssignmentOptions(): void {
    this.groupsLoading.set(true);
    this.groupsError.set(null);
    this.groupsApi.getAccessible().pipe(
      take(1),
      catchError(() => {
        this.groupsError.set('Nie udało się pobrać grup treningowych.');
        return EMPTY;
      }),
      finalize(() => this.groupsLoading.set(false)),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe((groups) => this.groups.set(groups));

    this.athletesLoading.set(true);
    this.athletesError.set(null);
    this.athletesApi.getAll().pipe(
      take(1),
      catchError(() => {
        this.athletesError.set('Nie udało się pobrać listy zawodników.');
        return EMPTY;
      }),
      finalize(() => this.athletesLoading.set(false)),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe((athletes) => this.athletes.set(athletes));
  }

  private setSaving(saving: boolean): void {
    this.saving.set(saving);
    this.savingChange.emit(saving);
  }

  private handleError(error: unknown): void {
    if (!(error instanceof HttpErrorResponse)) {
      this.errorMessage.set('Wystąpił nieoczekiwany błąd. Spróbuj ponownie.');
      return;
    }
    const body = error.error as { title?: string; detail?: string; errors?: Record<string, unknown> } | null;
    const fieldErrors: Record<string, string> = {};
    for (const field of Object.keys(body?.errors ?? {})) {
      if (field === 'username') fieldErrors['username'] = 'Wpisz nazwę użytkownika (maksymalnie 100 znaków).';
      if (field === 'password') fieldErrors['password'] = 'Wpisz hasło (maksymalnie 1024 znaki).';
      if (field === 'trainingGroupIds') fieldErrors['trainingGroupIds'] = 'Wybierz prawidłowe grupy treningowe.';
      if (field === 'athleteId') fieldErrors['athleteId'] = 'Wybierz prawidłowego zawodnika.';
      if (field === 'role') fieldErrors['role'] = 'Wybierz prawidłową rolę.';
    }
    this.serverFieldErrors.set(fieldErrors);

    if (error.status === 401) {
      this.errorMessage.set('Sesja wygasła lub użytkownik nie jest zalogowany.');
    } else if (error.status === 403) {
      this.errorMessage.set('Brak uprawnień do zarządzania kontami.');
    } else if (error.status === 409 && body?.title === 'Administrator required') {
      this.errorMessage.set('Nie można zmienić roli ostatniego aktywnego administratora.');
    } else if (error.status === 409 && body?.detail?.toLowerCase().includes('username')) {
      this.errorMessage.set('Ta nazwa użytkownika jest już zajęta.');
    } else if (error.status === 409 && body?.detail?.toLowerCase().includes('athlete')) {
      this.errorMessage.set('Ten zawodnik ma już przypisane konto.');
    } else if (error.status === 400) {
      this.errorMessage.set(this.translateAssignmentError(body?.detail) ?? 'Sprawdź poprawność danych i spróbuj ponownie.');
    } else if (error.status === 404) {
      this.errorMessage.set('Nie znaleziono wskazanej grupy, zawodnika lub konta.');
    } else {
      this.errorMessage.set(this.mode === 'CREATE'
        ? 'Nie udało się utworzyć konta. Spróbuj ponownie.'
        : 'Nie udało się zapisać zmian konta. Spróbuj ponownie.');
    }
  }

  private translateAssignmentError(detail?: string): string | null {
    if (!detail) return null;
    if (detail.includes('ADMIN accounts cannot have assignments')) {
      return 'Konto administratora nie może mieć przypisanej grupy ani zawodnika.';
    }
    if (detail.includes('COACH accounts require training groups')) {
      return 'Konto trenera wymaga co najmniej jednej grupy i nie może mieć przypisanego zawodnika.';
    }
    if (detail.includes('ATHLETE accounts require exactly one athlete')) {
      return 'Konto zawodnika wymaga dokładnie jednego zawodnika i nie może mieć przypisanej grupy.';
    }
    if (detail.includes('Training group IDs must be unique')) {
      return 'Każda grupa może być wybrana tylko raz.';
    }
    return null;
  }
}

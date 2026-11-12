import { HttpErrorResponse } from '@angular/common/http';
import { Component, EventEmitter, Input, OnInit, Output, computed, inject, signal } from '@angular/core';
import { FormControl, FormGroup, FormRecord, ReactiveFormsModule, ValidatorFn, Validators } from '@angular/forms';
import { EMPTY, catchError, finalize, take } from 'rxjs';
import { AthleteResponse } from '../athletes/athlete.model';
import { TestResponse } from '../tests/test.model';
import { SearchableSelectComponent, SearchableSelectOption } from '../../shared/searchable-select.component';
import {
  CreateMultipleResultsRequest,
  CreateMultipleResultsResponse,
  ResultType,
} from './result.model';
import { ResultsApiService } from './results-api.service';

const resultTypes: ResultType[] = ['TRAINING', 'COMPETITION'];
const positiveValue: ValidatorFn = (control) => {
  const value = control.value;
  if (typeof value !== 'string' || value.trim() === '') return null;
  const parsed = Number(value.replace(',', '.'));
  return Number.isFinite(parsed) && parsed > 0 ? null : { positive: true };
};
const resultPrecision: ValidatorFn = (control) => {
  const value = control.value;
  if (typeof value !== 'string' || value.trim() === '') return null;
  return /^(?=.{1,13}$)\d{1,9}(?:[.,]\d{1,3})?$/.test(value) ? null : { precision: true };
};
const validDate: ValidatorFn = (control) => {
  const value = control.value;
  if (typeof value !== 'string' || value === '') return null;
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value);
  if (!match) return { date: true };
  const [, year, month, day] = match;
  const parsed = new Date(Date.UTC(Number(year), Number(month) - 1, Number(day)));
  return parsed.getUTCFullYear() === Number(year) &&
    parsed.getUTCMonth() === Number(month) - 1 &&
    parsed.getUTCDate() === Number(day)
    ? null
    : { date: true };
};

@Component({
  imports: [ReactiveFormsModule, SearchableSelectComponent],
  selector: 'app-multiple-results-form',
  templateUrl: './multiple-results-form.component.html',
  styleUrl: './multiple-results-form.component.scss',
})
export class MultipleResultsFormComponent implements OnInit {
  @Input({ required: true }) athletes: AthleteResponse[] = [];
  @Input({ required: true }) tests: TestResponse[] = [];
  @Output() saved = new EventEmitter<CreateMultipleResultsResponse>();
  @Output() savingChange = new EventEmitter<boolean>();

  readonly saving = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly formMessage = signal<string | null>(null);
  readonly selectedTestId = signal('');
  private readonly lastSelectedTestId = signal('');
  readonly athleteSearch = signal('');
  readonly visibleAthletes = computed(() => {
    const search = this.athleteSearch().trim().toLocaleLowerCase('pl-PL');
    return this.activeAthletes().filter((athlete) =>
      `${athlete.firstName} ${athlete.lastName}`.toLocaleLowerCase('pl-PL').includes(search),
    );
  });
  readonly currentTest = computed(() =>
    this.tests.find((test) => test.id === Number(this.selectedTestId()) && test.active) ?? null,
  );
  readonly activeAthletes = computed(() => {
    const athleteIds = new Set<number>();
    return this.athletes.filter((athlete) => {
      if (!athlete.active || athleteIds.has(athlete.id)) return false;
      athleteIds.add(athlete.id);
      return true;
    });
  });
  readonly resultValues = new FormRecord<FormControl<string>>({});
  readonly form = new FormGroup({
    testId: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    resultDate: new FormControl(this.today(), {
      nonNullable: true,
      validators: [Validators.required, validDate],
    }),
    type: new FormControl<ResultType | ''>('', {
      nonNullable: true,
      validators: [Validators.required],
    }),
  });

  private readonly api = inject(ResultsApiService);

  ngOnInit(): void {
    for (const athlete of this.activeAthletes()) {
      this.resultValues.addControl(
        String(athlete.id),
        new FormControl('', { nonNullable: true, validators: [positiveValue, resultPrecision] }),
      );
    }
  }

  get testOptions(): SearchableSelectOption[] {
    return this.tests
      .filter((test) => test.active)
      .map((test) => ({ value: String(test.id), label: test.name }));
  }

  onTestSelected(testId: string): void {
    this.selectedTestId.set(testId);
    if (!testId) return;

    const previousTestId = this.lastSelectedTestId();
    this.lastSelectedTestId.set(testId);
    if (previousTestId && previousTestId !== testId) {
      for (const control of Object.values(this.resultValues.controls)) {
        control.reset('');
      }
      this.formMessage.set('Po zmianie testu wpisane wyniki zostały wyczyszczone.');
    } else {
      this.formMessage.set(null);
    }
  }

  isActiveTest(test: TestResponse): boolean {
    return test.active;
  }

  onAthleteSearch(event: Event): void {
    const input = event.target;
    if (input instanceof HTMLInputElement) this.athleteSearch.set(input.value);
  }

  resultControl(athleteId: number): FormControl<string> {
    return this.resultValues.controls[String(athleteId)];
  }

  fieldError(field: 'testId' | 'resultDate' | 'type'): string | null {
    const control = this.form.controls[field];
    if (control.invalid && (control.touched || control.dirty)) {
      if (control.hasError('required')) {
        return field === 'testId'
          ? 'Wybierz test.'
          : field === 'resultDate'
            ? 'Wybierz datę.'
            : 'Wybierz rodzaj wyniku.';
      }
      if (field === 'resultDate' && control.hasError('date')) {
        return 'Wpisz prawidłową datę.';
      }
    }
    return null;
  }

  resultError(athleteId: number): string | null {
    const control = this.resultControl(athleteId);
    if (control.invalid && (control.touched || control.dirty)) {
      if (control.hasError('positive')) return 'Wynik musi być większy od zera.';
      if (control.hasError('precision')) {
        return 'Wynik może mieć maksymalnie 9 cyfr przed przecinkiem i 3 po przecinku.';
      }
    }
    return null;
  }

  submit(): void {
    if (this.saving()) return;

    this.errorMessage.set(null);
    this.formMessage.set(null);
    this.form.markAllAsTouched();
    const entries = this.activeAthletes()
      .map((athlete) => ({ athlete, control: this.resultControl(athlete.id) }))
      .filter(({ control }) => control.value.trim() !== '');
    for (const { control } of entries) {
      control.markAsTouched();
    }

    const testId = Number(this.form.controls.testId.value);
    const selectedTestIsActive = this.tests.some((test) => test.id === testId && test.active);
    const type = this.form.controls.type.value;
    if (
      this.form.invalid ||
      !selectedTestIsActive ||
      !this.isResultType(type) ||
      entries.length === 0 ||
      entries.length > 100 ||
      entries.some(({ control }) => control.invalid)
    ) {
      if (entries.length === 0) {
        this.formMessage.set('Wpisz co najmniej jeden wynik.');
      } else if (entries.length > 100) {
        this.formMessage.set('Można zapisać maksymalnie 100 wyników naraz.');
      }
      return;
    }

    const request: CreateMultipleResultsRequest = {
      testId,
      resultDate: this.form.controls.resultDate.value,
      type,
      results: entries.map(({ athlete, control }) => ({
        athleteId: athlete.id,
        value: Number(control.value.replace(',', '.')),
      })),
    };

    this.setSaving(true);
    this.api
      .createMultipleResults(request)
      .pipe(
        take(1),
        catchError((error: unknown) => {
          this.handleError(error);
          return EMPTY;
        }),
        finalize(() => this.setSaving(false)),
      )
      .subscribe((response) => this.saved.emit(response));
  }

  private today(): string {
    const now = new Date();
    const month = String(now.getMonth() + 1).padStart(2, '0');
    const day = String(now.getDate()).padStart(2, '0');
    return `${now.getFullYear()}-${month}-${day}`;
  }

  private isResultType(value: string): value is ResultType {
    return resultTypes.includes(value as ResultType);
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

    const body = error.error as { title?: string; errors?: Record<string, unknown> } | null;
    const validationErrors = body?.errors ?? {};
    if (Object.keys(validationErrors).length > 0) {
      this.errorMessage.set('Sprawdź poprawność danych i spróbuj ponownie.');
    } else if (error.status === 401) {
      this.errorMessage.set('Sesja wygasła lub użytkownik nie jest zalogowany.');
    } else if (error.status === 403) {
      this.errorMessage.set('Brak dostępu do dodania wyników.');
    } else if (error.status === 404 && body?.title === 'Athlete not found') {
      this.errorMessage.set('Nie znaleziono jednego z wybranych zawodników.');
    } else if (error.status === 404 && body?.title === 'Test not found') {
      this.errorMessage.set('Nie znaleziono wybranego testu.');
    } else if (error.status === 409 && body?.title === 'Athlete is inactive') {
      this.errorMessage.set('Jeden z zawodników jest nieaktywny. Odśwież listę i spróbuj ponownie.');
    } else if (error.status === 409 && body?.title === 'Test is inactive') {
      this.errorMessage.set('Wybrany test jest nieaktywny. Wybierz inny test.');
    } else if (error.status === 400) {
      this.errorMessage.set('Sprawdź poprawność danych i spróbuj ponownie.');
    } else {
      this.errorMessage.set('Nie udało się dodać wyników. Spróbuj ponownie.');
    }
  }
}

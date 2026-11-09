import { HttpErrorResponse } from '@angular/common/http';
import { Component, EventEmitter, Input, OnInit, Output, computed, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, ValidatorFn, Validators } from '@angular/forms';
import { EMPTY, catchError, finalize, take } from 'rxjs';
import { AthleteResponse } from '../athletes/athlete.model';
import { TestResponse } from '../tests/test.model';
import { SearchableSelectComponent, SearchableSelectOption } from './searchable-select.component';
import {
  CreateResultRequest,
  ResultFormMode,
  ResultResponse,
  ResultType,
  UpdateResultRequest,
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
  selector: 'app-result-form',
  templateUrl: './result-form.component.html',
  styleUrl: './result-form.component.scss',
})
export class ResultFormComponent implements OnInit {
  @Input({ required: true }) mode: ResultFormMode = 'CREATE';
  @Input({ required: true }) athletes: AthleteResponse[] = [];
  @Input({ required: true }) tests: TestResponse[] = [];
  @Input() result: ResultResponse | null = null;
  @Input() initialAthleteId: number | null = null;
  @Output() saved = new EventEmitter<ResultResponse>();
  @Output() savingChange = new EventEmitter<boolean>();

  readonly saving = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly selectedTestId = signal('');
  readonly currentTest = computed(() => this.tests.find((test) => test.id === Number(this.selectedTestId())) ?? null);
  readonly resultTypes = resultTypes;
  readonly form = new FormGroup({
    athleteId: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    testId: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    resultDate: new FormControl(this.today(), {
      nonNullable: true,
      validators: [Validators.required, validDate],
    }),
    value: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, positiveValue, resultPrecision],
    }),
    type: new FormControl<ResultType | ''>('', {
      nonNullable: true,
      validators: [Validators.required],
    }),
  });

  private readonly api = inject(ResultsApiService);

  ngOnInit(): void {
    if (this.mode === 'EDIT' && this.result) {
      this.form.setValue({
        athleteId: String(this.result.athleteId),
        testId: String(this.result.testId),
        resultDate: this.result.resultDate,
        value: String(this.result.value),
        type: this.result.type,
      });
      this.selectedTestId.set(String(this.result.testId));
    } else if (this.mode === 'CREATE' && this.initialAthleteId !== null) {
      this.form.controls.athleteId.setValue(String(this.initialAthleteId));
    }
  }

  get athleteOptions(): SearchableSelectOption[] {
    return this.athletes
      .filter((athlete) => this.mode === 'EDIT' || athlete.active)
      .map((athlete) => ({
        value: String(athlete.id),
        label: `${athlete.firstName} ${athlete.lastName}${athlete.active ? '' : ' (nieaktywny)'}`,
      }));
  }

  get testOptions(): SearchableSelectOption[] {
    const options = this.tests
      .filter((test) => this.isActiveTest(test))
      .map((test) => ({
        value: String(test.id),
        label: `${test.name}${test.active ? '' : ' (nieaktywny)'}`,
      }));
    if (this.mode === 'EDIT' && this.result && !this.tests.some((test) => test.id === this.result?.testId)) {
      options.push({
        value: String(this.result.testId),
        label: `Test #${this.result.testId} (nieaktywny — bieżący)`,
      });
    }
    return options;
  }

  onTestSelected(testId: string): void {
    this.selectedTestId.set(testId);
  }

  isActiveAthlete(athlete: AthleteResponse): boolean {
    return athlete.active;
  }

  isActiveTest(test: TestResponse): boolean {
    return test.active || (this.mode === 'EDIT' && test.id === this.result?.testId);
  }

  fieldError(field: 'athleteId' | 'testId' | 'resultDate' | 'value' | 'type'): string | null {
    const control = this.form.controls[field];
    if (control.invalid && (control.touched || control.dirty)) {
      if (control.hasError('required')) {
        return field === 'athleteId'
          ? 'Wybierz zawodnika.'
          : field === 'testId'
            ? 'Wybierz test.'
            : field === 'resultDate'
              ? 'Wybierz datę.'
              : field === 'value'
                ? 'Wpisz wynik.'
                : 'Wybierz rodzaj wyniku.';
      }
      if (field === 'value' && control.hasError('positive')) {
        return 'Wynik musi być większy od zera.';
      }
      if (field === 'value' && control.hasError('precision')) {
        return 'Wynik może mieć maksymalnie 9 cyfr przed przecinkiem i 3 po przecinku.';
      }
      if (field === 'resultDate' && control.hasError('date')) {
        return 'Wpisz prawidłową datę.';
      }
    }
    return null;
  }

  submit(): void {
    if (this.saving()) return;

    this.errorMessage.set(null);
    this.form.markAllAsTouched();
    const { athleteId, testId, resultDate, value, type } = this.form.getRawValue();

    if (
      this.form.invalid ||
      !this.isResultType(type) ||
      !this.athletes.some((athlete) =>
        athlete.id === Number(athleteId) && (this.mode === 'EDIT' || athlete.active),
      ) ||
      !this.tests.some((test) =>
        test.id === Number(testId) && (test.active || (this.mode === 'EDIT' && test.id === this.result?.testId)),
      ) && !(this.mode === 'EDIT' && Number(testId) === this.result?.testId)
    ) {
      return;
    }

    if (this.mode === 'EDIT' && !this.result) {
      this.errorMessage.set('Nie można określić edytowanego wyniku.');
      return;
    }

    const request = {
      athleteId: Number(athleteId),
      testId: Number(testId),
      resultDate,
      value: Number(value.replace(',', '.')),
      type,
    };

    this.setSaving(true);
    const saveRequest = this.mode === 'CREATE'
      ? this.api.createResult(request satisfies CreateResultRequest)
      : this.api.updateResult(this.result!.id, request satisfies UpdateResultRequest);

    saveRequest
      .pipe(
        take(1),
        catchError((error: unknown) => {
          this.handleError(error);
          return EMPTY;
        }),
        finalize(() => this.setSaving(false)),
      )
      .subscribe((result) => this.saved.emit(result));
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
      return;
    }

    if (error.status === 401) {
      this.errorMessage.set('Sesja wygasła lub użytkownik nie jest zalogowany.');
    } else if (error.status === 403) {
      this.errorMessage.set(this.mode === 'CREATE' ? 'Brak dostępu do dodania wyniku.' : 'Brak dostępu do edycji wyniku.');
    } else if (error.status === 404 && body?.title === 'Athlete not found') {
      this.errorMessage.set('Nie znaleziono wybranego zawodnika.');
    } else if (error.status === 404 && body?.title === 'Test not found') {
      this.errorMessage.set('Nie znaleziono wybranego testu.');
    } else if (error.status === 404 && this.mode === 'EDIT') {
      this.errorMessage.set('Nie znaleziono wyniku do edycji.');
    } else if (error.status === 409 && body?.title === 'Athlete is inactive') {
      this.errorMessage.set('Nie można dodać wyniku zawodnikowi nieaktywnemu. Wybierz aktywnego zawodnika.');
    } else if (error.status === 409 && body?.title === 'Test is inactive') {
      this.errorMessage.set(
        this.mode === 'CREATE'
          ? 'Wybrany test jest nieaktywny. Wybierz inny test.'
          : 'Nie można przypisać wyniku do nieaktywnego testu. Wybierz aktywny test.',
      );
    } else if (error.status === 400) {
      this.errorMessage.set('Sprawdź poprawność danych i spróbuj ponownie.');
    } else {
      this.errorMessage.set(
        this.mode === 'CREATE'
          ? 'Nie udało się dodać wyniku. Spróbuj ponownie.'
          : 'Nie udało się zapisać zmian wyniku. Spróbuj ponownie.',
      );
    }
  }
}

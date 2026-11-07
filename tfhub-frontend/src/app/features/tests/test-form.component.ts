import { HttpErrorResponse } from '@angular/common/http';
import { Component, EventEmitter, Input, Output, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, ValidatorFn, Validators } from '@angular/forms';
import { EMPTY, catchError, finalize, take } from 'rxjs';
import {
  CreateTestRequest,
  TestCategory,
  TestFormMode,
  TestResponse,
  UpdateTestRequest,
} from './test.model';
import { TestsApiService } from './tests-api.service';

const categories: TestCategory[] = ['TIME', 'DISTANCE', 'SPEED'];
const trimmedRequired: ValidatorFn = (control) =>
  typeof control.value === 'string' && control.value.trim().length === 0
    ? { required: true }
    : null;
const trimmedMaxLength = (max: number): ValidatorFn => (control) =>
  typeof control.value === 'string' && control.value.trim().length > max
    ? { maxlength: { requiredLength: max } }
    : null;

@Component({
  imports: [ReactiveFormsModule],
  selector: 'app-test-form',
  templateUrl: './test-form.component.html',
  styleUrl: './test-form.component.scss',
})
export class TestFormComponent {
  @Input({ required: true }) mode!: TestFormMode;
  @Input() test: TestResponse | null = null;
  @Output() saved = new EventEmitter<TestResponse>();
  @Output() savingChange = new EventEmitter<boolean>();

  readonly saving = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly serverFieldErrors = signal<Record<string, string>>({});
  readonly categories = categories;
  readonly form = new FormGroup({
    name: new FormControl('', {
      nonNullable: true,
      validators: [trimmedRequired, trimmedMaxLength(150)],
    }),
    unit: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.pattern(/^(s|m|m\/s)$/)],
    }),
    category: new FormControl<TestCategory | ''>('', {
      nonNullable: true,
      validators: [Validators.required],
    }),
    higherIsBetter: new FormControl(false, { nonNullable: true }),
  });

  private readonly api = inject(TestsApiService);

  ngOnInit(): void {
    if (this.mode === 'EDIT' && this.test) {
      this.form.setValue({
        name: this.test.name,
        unit: this.test.unit,
        category: this.test.category,
        higherIsBetter: this.test.higherIsBetter,
      });
    }
  }

  fieldError(field: 'name' | 'unit' | 'category'): string | null {
    const control = this.form.controls[field];
    if (control.invalid && (control.touched || control.dirty)) {
      if (control.hasError('required')) {
        return field === 'name' ? 'Wpisz nazwę testu.' : 'Wybierz wartość.';
      }
      if (field === 'name' && control.hasError('maxlength')) {
        return 'Nazwa może mieć maksymalnie 150 znaków.';
      }
      if (field === 'unit' && control.hasError('pattern')) {
        return 'Wybierz jedną z dostępnych jednostek.';
      }
    }
    return this.serverFieldErrors()[field] ?? null;
  }

  submit(): void {
    this.errorMessage.set(null);
    this.serverFieldErrors.set({});
    this.form.markAllAsTouched();
    const category = this.form.controls.category.value;

    if (this.form.invalid || !this.isTestCategory(category)) {
      return;
    }

    const request = {
      name: this.form.controls.name.value.trim(),
      unit: this.form.controls.unit.value,
      category,
      higherIsBetter: this.form.controls.higherIsBetter.value,
    };

    if (this.mode === 'EDIT' && !this.test) {
      this.errorMessage.set('Nie można określić edytowanego testu.');
      return;
    }

    this.setSaving(true);
    const saveRequest =
      this.mode === 'CREATE'
        ? this.api.createTest(request satisfies CreateTestRequest)
        : this.api.updateTest(this.test!.id, request satisfies UpdateTestRequest);

    saveRequest
      .pipe(
        take(1),
        catchError((error: unknown) => {
          this.handleError(error);
          return EMPTY;
        }),
        finalize(() => this.setSaving(false)),
      )
      .subscribe((test) => this.saved.emit(test));
  }

  private setSaving(saving: boolean): void {
    this.saving.set(saving);
    this.savingChange.emit(saving);
  }

  private isTestCategory(value: string): value is TestCategory {
    return categories.includes(value as TestCategory);
  }

  private handleError(error: unknown): void {
    if (!(error instanceof HttpErrorResponse)) {
      this.errorMessage.set('Wystąpił nieoczekiwany błąd. Spróbuj ponownie.');
      return;
    }

    const body = error.error as { title?: string; errors?: Record<string, unknown> } | null;
    const fieldErrors: Record<string, string> = {};
    for (const field of Object.keys(body?.errors ?? {})) {
      if (field === 'name') {
        fieldErrors[field] = 'Nazwa testu jest nieprawidłowa lub przekracza 150 znaków.';
      } else if (field === 'unit') {
        fieldErrors[field] = 'Wybierz jedną z dostępnych jednostek.';
      } else if (field === 'category') {
        fieldErrors[field] = 'Wybierz prawidłową kategorię.';
      }
    }
    this.serverFieldErrors.set(fieldErrors);

    if (error.status === 401) {
      this.errorMessage.set('Sesja wygasła lub użytkownik nie jest zalogowany.');
    } else if (error.status === 403) {
      this.errorMessage.set('Brak dostępu do zapisania testu.');
    } else if (error.status === 409 && body?.title === 'Test already exists') {
      this.errorMessage.set('Test o tej nazwie już istnieje.');
    } else if (error.status === 409 && body?.title === 'Test configuration cannot be changed') {
      this.errorMessage.set('Nie można zmienić jednostki ani kategorii testu, który ma przypisane wyniki.');
    } else if (error.status === 404) {
      this.errorMessage.set('Nie znaleziono testu do edycji.');
    } else if (error.status === 400) {
      this.errorMessage.set('Sprawdź poprawność danych i spróbuj ponownie.');
    } else {
      this.errorMessage.set('Nie udało się zapisać testu. Spróbuj ponownie.');
    }
  }
}

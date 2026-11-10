import { HttpErrorResponse } from '@angular/common/http';
import { AfterViewInit, Component, ElementRef, EventEmitter, Input, Output, ViewChild, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, ValidatorFn, Validators } from '@angular/forms';
import { EMPTY, catchError, finalize, take } from 'rxjs';
import { AccountResponse } from './account.model';
import { AccountsApiService } from './accounts-api.service';

const passwordsMatch: ValidatorFn = (control) => {
  const form = control as FormGroup<{ newPassword: FormControl<string>; repeatPassword: FormControl<string> }>;
  return form.controls.newPassword.value === form.controls.repeatPassword.value
    ? null
    : { passwordsMismatch: true };
};
const notBlank: ValidatorFn = (control) =>
  typeof control.value === 'string' && control.value.trim().length === 0 ? { required: true } : null;

@Component({
  imports: [ReactiveFormsModule],
  selector: 'app-reset-account-password-modal',
  templateUrl: './reset-account-password-modal.component.html',
  styleUrl: './reset-account-password-modal.component.scss',
})
export class ResetAccountPasswordModalComponent implements AfterViewInit {
  @Input({ required: true }) account!: AccountResponse;
  @Output() reset = new EventEmitter<void>();
  @Output() cancelled = new EventEmitter<void>();
  @ViewChild('dialog') private dialog!: ElementRef<HTMLDialogElement>;

  readonly saving = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly form = new FormGroup({
    newPassword: new FormControl('', { nonNullable: true, validators: [notBlank, Validators.maxLength(1024)] }),
    repeatPassword: new FormControl('', { nonNullable: true, validators: [notBlank, Validators.maxLength(1024)] }),
  }, { validators: passwordsMatch });

  private readonly api = inject(AccountsApiService);

  ngAfterViewInit(): void {
    const dialog = this.dialog.nativeElement;
    if (typeof dialog.showModal === 'function') dialog.showModal();
    else dialog.setAttribute('open', '');
  }

  fieldError(field: 'newPassword' | 'repeatPassword'): string | null {
    const control = this.form.controls[field];
    if (control.invalid && (control.touched || control.dirty)) {
      if (control.hasError('required')) return 'To pole jest wymagane.';
      if (control.hasError('maxlength')) return 'Hasło może mieć maksymalnie 1024 znaki.';
    }
    if (field === 'repeatPassword'
      && this.form.hasError('passwordsMismatch')
      && (control.touched || control.dirty)) {
      return 'Hasła muszą być identyczne.';
    }
    return null;
  }

  submit(): void {
    if (this.saving()) return;
    this.errorMessage.set(null);
    this.form.markAllAsTouched();
    if (this.form.invalid) return;

    this.saving.set(true);
    this.api.resetPassword(this.account.id, { newPassword: this.form.controls.newPassword.value }).pipe(
      take(1),
      catchError((error: unknown) => {
        this.errorMessage.set(this.getErrorMessage(error));
        return EMPTY;
      }),
      finalize(() => this.saving.set(false)),
    ).subscribe(() => {
      this.form.reset({ newPassword: '', repeatPassword: '' });
      this.closeDialog();
      this.reset.emit();
    });
  }

  cancel(): void {
    if (this.saving()) return;
    this.clearForm();
    this.closeDialog();
    this.cancelled.emit();
  }

  onDialogCancel(event: Event): void {
    event.preventDefault();
    this.cancel();
  }

  private clearForm(): void {
    this.form.reset({ newPassword: '', repeatPassword: '' });
  }

  private closeDialog(): void {
    const dialog = this.dialog.nativeElement;
    if (typeof dialog.close === 'function') dialog.close();
    else dialog.removeAttribute('open');
  }

  private getErrorMessage(error: unknown): string {
    if (!(error instanceof HttpErrorResponse)) return 'Wystąpił nieoczekiwany błąd. Spróbuj ponownie.';
    if (error.status === 401) return 'Sesja wygasła lub użytkownik nie jest zalogowany.';
    if (error.status === 403) return 'Brak uprawnień do zmiany hasła.';
    if (error.status === 404) return 'Nie znaleziono konta.';
    if (error.status === 400) return 'Hasło nie spełnia wymagań. Sprawdź jego długość.';
    return 'Nie udało się zmienić hasła. Spróbuj ponownie.';
  }
}

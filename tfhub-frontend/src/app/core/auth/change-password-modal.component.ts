import { HttpErrorResponse } from '@angular/common/http';
import { AfterViewInit, Component, ElementRef, EventEmitter, Output, ViewChild, inject, signal } from '@angular/core';
import { AbstractControl, FormControl, FormGroup, ReactiveFormsModule, ValidationErrors, ValidatorFn, Validators } from '@angular/forms';
import { EMPTY, catchError, finalize, take } from 'rxjs';
import { AuthService } from './auth.service';

const passwordsMatch: ValidatorFn = (control: AbstractControl): ValidationErrors | null => {
  const newPassword = control.get('newPassword')?.value;
  const confirmation = control.get('passwordConfirmation')?.value;
  return newPassword && confirmation && newPassword !== confirmation ? { passwordsMismatch: true } : null;
};

const nonBlankPassword: ValidatorFn = (control: AbstractControl): ValidationErrors | null =>
  typeof control.value === 'string' && control.value.trim().length === 0 ? { required: true } : null;

@Component({
  imports: [ReactiveFormsModule],
  selector: 'app-change-password-modal',
  template: `
    <dialog #dialog class="modal" aria-labelledby="change-password-title" (cancel)="onDialogCancel($event)">
      <div class="modal-header">
        <h2 id="change-password-title">Zmień hasło</h2>
        <button
          type="button"
          class="button button--icon close-button"
          aria-label="Anuluj i zamknij okno"
          [disabled]="saving()"
          (click)="cancel()"
        >
          ×
        </button>
      </div>

      <div class="modal-content">
        <form id="change-password-form" [formGroup]="form" (ngSubmit)="submit()" novalidate>
          @if (errorMessage(); as message) {
            <p class="form-error" role="alert">{{ message }}</p>
          }
          <div class="field">
            <label for="current-password">Aktualne hasło <span aria-hidden="true">*</span></label>
            <input
              id="current-password"
              class="field-control"
              type="password"
              formControlName="currentPassword"
              autocomplete="current-password"
              [attr.aria-invalid]="fieldError('currentPassword') ? 'true' : null"
              aria-describedby="current-password-error"
              autofocus
            />
            @if (fieldError('currentPassword'); as message) {
              <span class="field-error" id="current-password-error">{{ message }}</span>
            }
          </div>
          <div class="field">
            <label for="new-password">Nowe hasło <span aria-hidden="true">*</span></label>
            <input
              id="new-password"
              class="field-control"
              type="password"
              formControlName="newPassword"
              autocomplete="new-password"
              [attr.aria-invalid]="fieldError('newPassword') ? 'true' : null"
              aria-describedby="new-password-error"
            />
            @if (fieldError('newPassword'); as message) {
              <span class="field-error" id="new-password-error">{{ message }}</span>
            }
          </div>
          <div class="field">
            <label for="password-confirmation">Powtórz nowe hasło <span aria-hidden="true">*</span></label>
            <input
              id="password-confirmation"
              class="field-control"
              type="password"
              formControlName="passwordConfirmation"
              autocomplete="new-password"
              [attr.aria-invalid]="fieldError('passwordConfirmation') ? 'true' : null"
              aria-describedby="password-confirmation-error"
            />
            @if (fieldError('passwordConfirmation'); as message) {
              <span class="field-error" id="password-confirmation-error">{{ message }}</span>
            }
          </div>
        </form>
      </div>

      <footer class="modal-footer">
        <button type="button" class="button button--secondary cancel-button" [disabled]="saving()" (click)="cancel()">
          Anuluj
        </button>
        <button
          type="submit"
          form="change-password-form"
          class="button button--primary save-button"
          [disabled]="form.invalid || saving()"
        >
          {{ saving() ? 'Zapisywanie...' : 'Zapisz' }}
        </button>
      </footer>
    </dialog>
  `,
  styles: `
    :host { display: contents; }
    .modal {
      border: 0;
      border-radius: .8rem;
      box-shadow: 0 1.2rem 3.5rem rgb(15 23 42 / 25%);
      color: #172033;
      max-height: min(90dvh, 52rem);
      max-width: min(32rem, calc(100vw - 2rem));
      overflow: auto;
      padding: 0;
      width: 100%;
    }
    .modal::backdrop { background: rgb(19 27 40 / 48%); }
    .modal-header { align-items: center; border-bottom: 1px solid #dce1ea; display: flex; justify-content: space-between; padding: 1.25rem 1.5rem; }
    .modal-header h2 { font-size: 1.65rem; letter-spacing: -.025em; margin: 0; }
    .modal-content { padding: 1.5rem; }
    form { display: grid; gap: 1rem; }
    .field { display: grid; gap: .4rem; }
    label { color: #273248; font-weight: 600; }
    .field-control { border: 1px solid #cbd1d9; border-radius: .45rem; font: inherit; min-width: 0; padding: .65rem .75rem; }
    .field-control[aria-invalid="true"] { border-color: #b4232d; }
    .field-error, .form-error { color: #a3222b; font-size: .9rem; margin: 0; }
    .form-error { margin-bottom: .15rem; }
    .modal-footer { border-top: 1px solid #dce1ea; display: flex; gap: .7rem; justify-content: flex-end; padding: 1rem 1.5rem; }
    .button { border: 1px solid transparent; border-radius: .45rem; cursor: pointer; font: inherit; font-weight: 600; padding: .55rem .85rem; }
    .button:disabled { cursor: not-allowed; opacity: .6; }
    .button--primary { background: #198754; color: #fff; }
    .button--secondary, .button--icon { background: #fff; border-color: #cbd1d9; color: #273248; }
    .button--icon { font-size: 1.2rem; line-height: 1; padding: .35rem .55rem; }
    @media (max-width: 520px) {
      .modal { border-radius: .7rem; max-height: 94dvh; max-width: calc(100vw - 1rem); }
      .modal-header { padding: 1rem; }
      .modal-content { padding: 1rem; }
      .modal-footer { padding: .8rem 1rem; }
    }
  `,
})
export class ChangePasswordModalComponent implements AfterViewInit {
  @Output() cancelled = new EventEmitter<void>();
  @Output() changed = new EventEmitter<void>();
  @ViewChild('dialog') private dialog!: ElementRef<HTMLDialogElement>;

  readonly saving = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly form = new FormGroup({
    currentPassword: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, nonBlankPassword, Validators.maxLength(1024)],
    }),
    newPassword: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, nonBlankPassword, Validators.maxLength(1024)],
    }),
    passwordConfirmation: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, nonBlankPassword, Validators.maxLength(1024)],
    }),
  }, { validators: passwordsMatch });

  private readonly auth = inject(AuthService);

  ngAfterViewInit(): void {
    const dialog = this.dialog.nativeElement;
    if (typeof dialog.showModal === 'function') {
      dialog.showModal();
    } else {
      dialog.setAttribute('open', '');
    }
  }

  fieldError(field: 'currentPassword' | 'newPassword' | 'passwordConfirmation'): string | null {
    const control = this.form.controls[field];
    if (!control.touched) return null;
    if (control.hasError('required')) return 'To pole jest wymagane.';
    if (control.hasError('maxlength')) return 'Hasło może mieć maksymalnie 1024 znaki.';
    if (field === 'passwordConfirmation' && this.form.hasError('passwordsMismatch')) {
      return 'Nowe hasła nie są zgodne.';
    }
    return null;
  }

  submit(): void {
    if (this.saving()) return;
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.errorMessage.set(null);
    this.saving.set(true);
    const { currentPassword, newPassword } = this.form.getRawValue();
    this.auth.changePassword(currentPassword, newPassword).pipe(
      take(1),
      catchError((error: unknown) => {
        if (error instanceof HttpErrorResponse && error.status === 401) {
          this.cancelled.emit();
          return EMPTY;
        }
        this.errorMessage.set(
          error instanceof HttpErrorResponse && error.status === 400
            ? 'Nie udało się zmienić hasła. Sprawdź aktualne hasło i poprawność nowych danych.'
            : 'Nie udało się zmienić hasła. Spróbuj ponownie.',
        );
        return EMPTY;
      }),
      finalize(() => this.saving.set(false)),
    ).subscribe(() => {
      this.form.reset();
      this.changed.emit();
    });
  }

  cancel(): void {
    if (this.saving()) return;
    this.closeDialog();
    this.form.reset();
    this.cancelled.emit();
  }

  onDialogCancel(event: Event): void {
    event.preventDefault();
    this.cancel();
  }

  private closeDialog(): void {
    const dialog = this.dialog.nativeElement;
    if (typeof dialog.close === 'function') {
      dialog.close();
    } else {
      dialog.removeAttribute('open');
    }
  }
}

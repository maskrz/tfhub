import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { EMPTY, catchError, finalize, map, of, switchMap, take } from 'rxjs';
import { AuthService } from './auth.service';

@Component({
  imports: [ReactiveFormsModule],
  selector: 'app-login-page',
  template: `
    <main class="login-page">
      <form class="login-card" [formGroup]="form" (ngSubmit)="submit()">
        <h1>Logowanie do TFHUB</h1>
        @if (notice()) {
          <p class="notice" role="status">{{ notice() }}</p>
        }
        <label>
          <span>Nazwa użytkownika</span>
          <input type="text" formControlName="username" autocomplete="username" required />
        </label>
        <label>
          <span>Hasło</span>
          <input type="password" formControlName="password" autocomplete="current-password" required />
        </label>
        @if (errorMessage()) {
          <p class="error" role="alert">{{ errorMessage() }}</p>
        }
        <button type="submit" [disabled]="form.invalid || submitting()">
          {{ submitting() ? 'Logowanie…' : 'Zaloguj się' }}
        </button>
      </form>
    </main>
  `,
  styles: `
    .login-page { display: grid; min-height: 65vh; place-items: center; padding: 2rem 1rem; }
    .login-card { background: white; border-radius: 1rem; box-shadow: 0 1rem 3rem #2026301a; display: grid; gap: 1.2rem; max-width: 26rem; padding: 2rem; width: 100%; }
    h1 { font-size: 1.5rem; margin: 0; }
    label { display: grid; gap: .45rem; }
    input { border: 1px solid #cbd1d9; border-radius: .5rem; font: inherit; padding: .7rem; }
    button { background: #198754; border: 0; border-radius: .5rem; color: white; cursor: pointer; font: inherit; font-weight: 650; padding: .8rem; }
    button:disabled { cursor: not-allowed; opacity: .6; }
    .error { color: #a3222b; margin: 0; }
    .notice { background: #edf8f0; border-radius: .45rem; color: #236b3a; margin: 0; padding: .75rem; }
  `,
})
export class LoginPage implements OnInit {
  readonly submitting = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly notice = signal<string | null>(null);
  readonly form = new FormGroup({
    username: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    password: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
  });

  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  ngOnInit(): void {
    if (this.route.snapshot.queryParamMap.get('notice') === 'account-password-reset') {
      this.notice.set('Hasło zostało zmienione. Zaloguj się ponownie.');
    }
    this.auth.ensureSession().pipe(
      take(1),
      catchError(() => of(null)),
    ).subscribe((user) => {
      if (user) void this.router.navigateByUrl(this.destination(user.role, user.athleteId));
    });
  }

  submit(): void {
    if (this.form.invalid || this.submitting()) return;
    this.errorMessage.set(null);
    this.submitting.set(true);
    this.auth.refreshCsrfToken().pipe(
      switchMap(() => this.auth.login(this.form.controls.username.value.trim(), this.form.controls.password.value)),
      switchMap((user) => this.auth.refreshCsrfToken().pipe(map(() => user))),
      take(1),
      catchError((error: unknown) => {
        this.errorMessage.set(
          error instanceof HttpErrorResponse && error.status === 401
            ? 'Nieprawidłowa nazwa użytkownika lub hasło.'
            : 'Nie udało się zalogować. Spróbuj ponownie.',
        );
        return EMPTY;
      }),
      finalize(() => this.submitting.set(false)),
    ).subscribe((user) => {
      const requestedUrl = this.route.snapshot.queryParamMap.get('returnUrl');
      void this.router.navigateByUrl(
        requestedUrl && requestedUrl.startsWith('/') ? requestedUrl : this.destination(user.role, user.athleteId),
      );
    });
  }

  private destination(role: string, athleteId: number | null): string {
    return role === 'ATHLETE' && athleteId !== null ? `/athletes/${athleteId}` : '/dashboard';
  }
}

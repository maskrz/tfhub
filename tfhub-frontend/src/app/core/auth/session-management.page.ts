import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { EMPTY, catchError, finalize, take } from 'rxjs';
import { UserSession } from './auth.model';
import { AuthService } from './auth.service';

@Component({
  imports: [DatePipe],
  selector: 'app-session-management-page',
  template: `
    <section class="page" aria-labelledby="page-title">
      <header class="page-header">
        <div>
          <h1 id="page-title">Aktywne sesje</h1>
          <p>Możesz zakończyć sesje, z których nie korzystasz.</p>
        </div>
      </header>

      @if (loading()) {
        <p role="status">Wczytywanie sesji…</p>
      } @else if (loadError()) {
        <section class="message message--error" role="alert">
          <p>{{ loadError() }}</p>
          <button type="button" class="button button--primary" (click)="loadSessions()">Spróbuj ponownie</button>
        </section>
      } @else if (sessions().length === 0) {
        <p class="empty-state">Nie znaleziono aktywnych sesji.</p>
      } @else {
        @if (actionError()) {
          <p class="message message--error" role="alert">{{ actionError() }}</p>
        }
        <ul class="session-list" aria-label="Lista aktywnych sesji">
          @for (session of sessions(); track session.id) {
            <li class="session-card">
              <div class="session-details">
                <h2>{{ session.current ? 'Ta sesja' : 'Inna sesja' }}</h2>
                @if (session.current) {
                  <span class="current-label">Bieżąca sesja</span>
                }
                <p><strong>Utworzono:</strong> {{ session.createdAt | date: 'dd.MM.yyyy, HH:mm' }}</p>
                <p><strong>Ostatnia aktywność:</strong> {{ session.lastActivityAt | date: 'dd.MM.yyyy, HH:mm' }}</p>
              </div>
              <button
                type="button"
                class="button button--danger"
                [disabled]="revokingHandle() !== null"
                (click)="revoke(session)"
              >
                {{ revokingHandle() === session.id ? 'Kończenie…' : 'Zakończ sesję' }}
              </button>
            </li>
          }
        </ul>
      }
    </section>
  `,
  styles: `
    .page { display: grid; gap: 1.25rem; margin: 0 auto; max-width: var(--page-width); padding: 2rem var(--page-gutter-mobile); }
    .page-header h1 { font-size: var(--heading-page-size); margin: 0; }
    .page-header p { color: var(--text-muted); margin: .5rem 0 0; }
    .session-list { display: grid; gap: 1rem; list-style: none; margin: 0; padding: 0; }
    .session-card { align-items: center; background: var(--surface-card); border: 1px solid var(--surface-border); border-radius: var(--card-radius); display: flex; gap: 1.5rem; justify-content: space-between; padding: 1.25rem; }
    .session-details h2 { font-size: 1.1rem; margin: 0; }
    .session-details p { margin: .4rem 0 0; }
    .current-label { background: var(--brand-tint); border-radius: 999px; color: var(--brand-primary); display: inline-block; font-size: .85rem; font-weight: 600; margin-top: .5rem; padding: .2rem .65rem; }
    .message { background: #fff; border-radius: var(--card-radius); padding: 1rem; }
    .message--error { border: 1px solid #d88a8e; color: #8d1e26; }
    .message p { margin: 0 0 .75rem; }
    .empty-state { background: var(--surface-card); border: 1px solid var(--surface-border); border-radius: var(--card-radius); margin: 0; padding: 1.25rem; }
    .session-card .button { flex: 0 0 auto; }
    @media (min-width: 700px) { .page { padding-inline: var(--page-gutter-desktop); } }
    @media (max-width: 560px) { .session-card { align-items: stretch; flex-direction: column; } .session-card .button { width: 100%; } }
  `,
})
export class SessionManagementPage implements OnInit {
  readonly sessions = signal<UserSession[]>([]);
  readonly loading = signal(true);
  readonly loadError = signal<string | null>(null);
  readonly actionError = signal<string | null>(null);
  readonly revokingHandle = signal<string | null>(null);

  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  ngOnInit(): void {
    this.loadSessions();
  }

  loadSessions(): void {
    this.loading.set(true);
    this.loadError.set(null);
    this.auth.getSessions().pipe(
      take(1),
      catchError((error: unknown) => {
        this.loadError.set(
          error instanceof HttpErrorResponse && error.status === 401
            ? 'Zaloguj się ponownie, aby wyświetlić aktywne sesje.'
            : 'Nie udało się wczytać sesji. Spróbuj ponownie.',
        );
        return EMPTY;
      }),
      finalize(() => this.loading.set(false)),
    ).subscribe((sessions) => this.sessions.set(sessions));
  }

  revoke(session: UserSession): void {
    if (this.revokingHandle() !== null) return;
    this.actionError.set(null);
    this.revokingHandle.set(session.id);
    this.auth.revokeSession(session.id).pipe(
      take(1),
      catchError(() => {
        this.actionError.set('Nie udało się zakończyć sesji. Odśwież listę i spróbuj ponownie.');
        return EMPTY;
      }),
      finalize(() => this.revokingHandle.set(null)),
    ).subscribe(() => {
      if (session.current) {
        this.auth.clearSession();
        void this.router.navigate(['/login']);
        return;
      }
      this.loadSessions();
    });
  }
}

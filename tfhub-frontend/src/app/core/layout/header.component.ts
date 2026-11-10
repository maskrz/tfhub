import { HttpErrorResponse } from '@angular/common/http';
import { Component, ElementRef, HostListener, OnDestroy, inject, signal } from '@angular/core';
import { Router, RouterLink, RouterLinkActive } from '@angular/router';
import { EMPTY, catchError, finalize, take } from 'rxjs';
import { AuthService } from '../auth/auth.service';
import { ChangePasswordModalComponent } from '../auth/change-password-modal.component';

@Component({
  imports: [ChangePasswordModalComponent, RouterLink, RouterLinkActive],
  selector: 'app-header',
  template: `
    <header class="app-header">
      <a class="brand" [routerLink]="user()?.role === 'ATHLETE' ? ['/athletes', user()?.athleteId] : '/dashboard'" aria-label="TFHUB — strona główna">
        TFHUB<span>.</span>
      </a>
      <nav aria-label="Nawigacja główna">
        @if (user()?.role !== 'ATHLETE') {
          <a routerLink="/dashboard" routerLinkActive="active" ariaCurrentWhenActive="page">Panel główny</a>
        }
        @if (user()?.role === 'ADMIN') {
          <a routerLink="/tests" routerLinkActive="active" ariaCurrentWhenActive="page">Testy</a>
        }
        @if (user()?.role === 'ADMIN' || user()?.role === 'COACH') {
          <a routerLink="/athletes" routerLinkActive="active" ariaCurrentWhenActive="page">Zawodnicy</a>
          <a routerLink="/results" routerLinkActive="active" ariaCurrentWhenActive="page">Wyniki</a>
        }
        @if (user()?.role === 'ATHLETE') {
          <a [routerLink]="['/athletes', user()?.athleteId]" routerLinkActive="active" ariaCurrentWhenActive="page">Moje wyniki</a>
        }
      </nav>
      @if (user(); as account) {
        <div class="account-menu" (click)="$event.stopPropagation()">
          <button
            class="username"
            type="button"
            aria-haspopup="menu"
            [attr.aria-expanded]="menuOpen()"
            (click)="toggleMenu()"
          >
            {{ account.username }}
          </button>
          @if (menuOpen()) {
            <div class="user-menu" role="menu" aria-label="Menu użytkownika">
              <button type="button" role="menuitem" (click)="openPasswordChange()">
                Zmień hasło
              </button>
              <button type="button" role="menuitem" (click)="logout()">Wyloguj</button>
            </div>
          }
        </div>
      } @else {
        <a class="login-link" routerLink="/login">Zaloguj</a>
        <span class="account" aria-label="Konto" title="Konto">
        <svg viewBox="0 0 24 24" aria-hidden="true">
          <circle cx="12" cy="8" r="4"></circle>
          <path d="M4 21a8 8 0 0 1 16 0"></path>
        </svg>
        </span>
      }
    </header>
    @if (passwordChangeOpen()) {
      <app-change-password-modal
        (cancelled)="passwordChangeOpen.set(false)"
        (changed)="onPasswordChanged()"
      />
    }
    @if (successMessage(); as message) {
      <div class="success-toast" role="status" aria-live="polite">
        <span>{{ message }}</span>
        <button type="button" aria-label="Zamknij powiadomienie" (click)="dismissSuccessMessage()">×</button>
      </div>
    }
  `,
  styles: `
    :host { display: block; }
    .app-header {
      align-items: center;
      background: #202630;
      color: #f7f8fa;
      display: flex;
      gap: 2rem;
      min-height: 5.6rem;
      padding: 0 clamp(1rem, 3vw, 2.5rem);
    }
    .brand {
      color: inherit;
      font-size: 1.65rem;
      font-weight: 750;
      letter-spacing: -.04em;
      text-decoration: none;
      white-space: nowrap;
    }
    .brand span { color: #41bf72; }
    nav {
      align-items: stretch;
      display: flex;
      flex: 1;
      gap: clamp(.5rem, 2vw, 2rem);
      height: 5.6rem;
    }
    nav a {
      align-items: center;
      border-bottom: 4px solid transparent;
      color: #d3d8e0;
      display: flex;
      font-weight: 550;
      padding: 0 .35rem;
      text-decoration: none;
      white-space: nowrap;
    }
    nav a.active {
      border-bottom-color: #42c66f;
      color: #fff;
    }
    .account {
      align-items: center;
      background: #66707c;
      border-radius: 50%;
      color: white;
      display: flex;
      height: 2.7rem;
      justify-content: center;
      width: 2.7rem;
    }
    .account-menu { position: relative; }
    .username {
      background: transparent;
      border: 0;
      color: #d3d8e0;
      cursor: pointer;
      font: inherit;
      font-size: .9rem;
      padding: .5rem;
    }
    .username:hover, .username:focus-visible { color: #fff; text-decoration: underline; }
    .user-menu {
      background: #fff;
      border: 1px solid #dce1ea;
      border-radius: .55rem;
      box-shadow: 0 .7rem 2rem rgb(15 23 42 / 20%);
      display: grid;
      min-width: 11rem;
      padding: .35rem;
      position: absolute;
      right: 0;
      top: calc(100% + .4rem);
      z-index: 20;
    }
    .user-menu button {
      background: transparent;
      border: 0;
      border-radius: .35rem;
      color: #172033;
      cursor: pointer;
      font: inherit;
      padding: .65rem .75rem;
      text-align: left;
    }
    .user-menu button:hover, .user-menu button:focus-visible { background: #f2f4f8; }
    .logout, .login-link {
      background: transparent;
      border: 1px solid #66707c;
      border-radius: .45rem;
      color: #fff;
      cursor: pointer;
      font: inherit;
      padding: .5rem .7rem;
      text-decoration: none;
    }
    .account svg {
      fill: none;
      height: 1.6rem;
      stroke: currentColor;
      stroke-linecap: round;
      stroke-width: 1.7;
      width: 1.6rem;
    }
    .success-toast {
      align-items: center;
      background: #edf8f0;
      border: 1px solid #b8dfc3;
      border-radius: .55rem;
      box-shadow: 0 .7rem 2rem rgb(15 23 42 / 16%);
      color: #236b3a;
      display: flex;
      gap: 1rem;
      justify-content: space-between;
      padding: .8rem 1rem;
      position: fixed;
      right: 1rem;
      top: 1rem;
      z-index: 50;
    }
    .success-toast button {
      background: transparent;
      border: 0;
      color: inherit;
      cursor: pointer;
      font: inherit;
      font-size: 1.25rem;
      line-height: 1;
    }
    @media (max-width: 650px) {
      .app-header { flex-wrap: wrap; gap: .25rem 1rem; padding-block: .6rem 0; }
      nav { flex-basis: 100%; gap: .5rem; height: 3.25rem; justify-content: space-between; order: 1; }
      nav a { font-size: .82rem; padding: 0 .15rem; }
      .account-menu, .account { margin-left: auto; }
    }
  `,
})
export class HeaderComponent implements OnDestroy {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly element = inject(ElementRef<HTMLElement>);
  readonly user = this.auth.currentUser;
  readonly menuOpen = signal(false);
  readonly passwordChangeOpen = signal(false);
  readonly successMessage = signal<string | null>(null);
  private successTimeout: ReturnType<typeof setTimeout> | null = null;

  @HostListener('document:click', ['$event'])
  closeMenuOnOutsideClick(event: MouseEvent): void {
    if (!this.element.nativeElement.querySelector('.account-menu')?.contains(event.target as Node)) {
      this.menuOpen.set(false);
    }
  }

  @HostListener('document:keydown.escape')
  closeMenuOnEscape(): void {
    this.menuOpen.set(false);
  }

  ngOnDestroy(): void {
    if (this.successTimeout !== null) {
      clearTimeout(this.successTimeout);
    }
  }

  toggleMenu(): void {
    this.menuOpen.update((open) => !open);
  }

  openPasswordChange(): void {
    this.menuOpen.set(false);
    this.passwordChangeOpen.set(true);
  }

  onPasswordChanged(): void {
    this.passwordChangeOpen.set(false);
    this.auth.clearSession();
    this.successMessage.set('Hasło zostało zmienione.');
    if (this.successTimeout !== null) {
      clearTimeout(this.successTimeout);
    }
    this.successTimeout = setTimeout(() => this.dismissSuccessMessage(), 6000);
    void this.router.navigate(['/login']);
  }

  dismissSuccessMessage(): void {
    this.successMessage.set(null);
    if (this.successTimeout !== null) {
      clearTimeout(this.successTimeout);
      this.successTimeout = null;
    }
  }

  logout(): void {
    this.menuOpen.set(false);
    this.auth.logout().pipe(
      take(1),
      finalize(() => void this.router.navigate(['/login'])),
      catchError(() => EMPTY),
    ).subscribe(() => {
    });
  }
}

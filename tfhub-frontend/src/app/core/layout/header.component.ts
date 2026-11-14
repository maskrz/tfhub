import { HttpErrorResponse } from '@angular/common/http';
import { Component, ElementRef, HostListener, OnDestroy, ViewChild, inject, signal } from '@angular/core';
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
        <img src="/tfhub-banner.png" alt="TFHub" />
      </a>
      <button #navigationButton class="navigation-toggle" type="button" aria-controls="main-navigation"
        [attr.aria-expanded]="navigationOpen()" (click)="toggleNavigation()">
        Menu
      </button>
      <nav id="main-navigation" aria-label="Nawigacja główna"
        [class.navigation-open]="navigationOpen()" (click)="onNavigationClick($event)">
        @if (user()?.role !== 'ATHLETE') {
          <a routerLink="/dashboard" routerLinkActive="active" ariaCurrentWhenActive="page">Dashboard</a>
        }
        @if (user()?.role === 'ADMIN') {
          <a routerLink="/tests" routerLinkActive="active" ariaCurrentWhenActive="page">Sprawdziany</a>
        }
        @if (user()?.role === 'ADMIN' || user()?.role === 'COACH') {
          <a routerLink="/athletes" routerLinkActive="active" ariaCurrentWhenActive="page">Zawodnicy</a>
          <a routerLink="/trainings" routerLinkActive="active" ariaCurrentWhenActive="page">Treningi</a>
          <a routerLink="/results" routerLinkActive="active" ariaCurrentWhenActive="page">Wyniki sprawdzianów</a>
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
              @if (account.role === 'ADMIN') {
                <a routerLink="/accounts" routerLinkActive="active" role="menuitem" (click)="menuOpen.set(false)">
                  Zarządzanie kontami
                </a>
              }
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
      position: relative;
    }
    .brand {
      align-items: center;
      color: inherit;
      display: flex;
      flex: 0 0 auto;
      text-decoration: none;
      white-space: nowrap;
    }
    .brand img {
      display: block;
      height: auto;
      width: clamp(9rem, 16vw, 14rem);
    }
    .navigation-toggle { display: none; }
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
      background: #35404e;
      border: 1px solid #596575;
      border-radius: .45rem;
      color: #fff;
      cursor: pointer;
      font: inherit;
      font-size: .9rem;
      max-width: 12rem;
      overflow: hidden;
      padding: .55rem .75rem;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
    .username:hover { background: #414e5e; }
    .username:focus-visible { outline: 2px solid #42c66f; outline-offset: 2px; }
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
    .user-menu button, .user-menu a {
      background: transparent;
      border: 0;
      border-radius: .35rem;
      color: #172033;
      cursor: pointer;
      display: block;
      font: inherit;
      padding: .65rem .75rem;
      text-align: left;
      text-decoration: none;
    }
    .user-menu button:hover, .user-menu button:focus-visible,
    .user-menu a:hover, .user-menu a:focus-visible, .user-menu a.active { background: #f2f4f8; }
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
    @media (max-width: 1000px) {
      .app-header { gap: .5rem; min-height: 4.5rem; }
      .brand { margin-right: auto; }
      .navigation-toggle, .username { box-sizing: border-box; height: 2.75rem; }
      .navigation-toggle {
        background: #35404e; border: 1px solid #596575; border-radius: .45rem;
        color: #fff; cursor: pointer; display: block; font: inherit;
        min-height: 2.75rem; padding: .5rem .75rem;
      }
      .navigation-toggle:focus-visible { outline: 2px solid #42c66f; outline-offset: 2px; }
      nav {
        align-items: stretch; background: #202630; box-shadow: 0 .7rem 2rem rgb(15 23 42 / 20%);
        display: none; flex: none; flex-direction: column; gap: 0; height: auto;
        left: 0; padding: .35rem 1rem .75rem; position: absolute; right: 0;
        top: 100%; z-index: 20;
      }
      nav.navigation-open { display: flex; }
      nav a { border-bottom: 0; border-left: 4px solid transparent; min-height: 2.75rem; padding: .5rem .75rem; white-space: normal; }
      nav a.active { border-left-color: #42c66f; background: #35404e; }
      .account-menu { min-width: 0; }
      .username { max-width: min(35vw, 12rem); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
      .account { flex-shrink: 0; }
    }
  `,
})
export class HeaderComponent implements OnDestroy {
  @ViewChild('navigationButton') private navigationButton?: ElementRef<HTMLButtonElement>;
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly element = inject(ElementRef<HTMLElement>);
  readonly user = this.auth.currentUser;
  readonly menuOpen = signal(false);
  readonly navigationOpen = signal(false);
  readonly passwordChangeOpen = signal(false);
  readonly successMessage = signal<string | null>(null);
  private successTimeout: ReturnType<typeof setTimeout> | null = null;

  @HostListener('document:click', ['$event'])
  closeMenuOnOutsideClick(event: MouseEvent): void {
    if (!this.element.nativeElement.querySelector('.account-menu')?.contains(event.target as Node)) {
      this.menuOpen.set(false);
    }
    if (!this.element.nativeElement.querySelector('nav')?.contains(event.target as Node)
        && !this.navigationButton?.nativeElement.contains(event.target as Node)) {
      this.navigationOpen.set(false);
    }
  }

  @HostListener('document:keydown.escape')
  closeMenuOnEscape(): void {
    if (this.navigationOpen()) this.navigationButton?.nativeElement.focus();
    this.navigationOpen.set(false);
    this.menuOpen.set(false);
  }

  ngOnDestroy(): void {
    if (this.successTimeout !== null) {
      clearTimeout(this.successTimeout);
    }
  }

  toggleMenu(): void {
    this.navigationOpen.set(false);
    this.menuOpen.update((open) => !open);
  }

  toggleNavigation(): void {
    this.menuOpen.set(false);
    this.navigationOpen.update((open) => !open);
  }

  onNavigationClick(event: MouseEvent): void {
    if (event.target instanceof Element && event.target.closest('a')) this.navigationOpen.set(false);
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

import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { of } from 'rxjs';
import { Component, signal } from '@angular/core';
import { AuthService } from '../auth/auth.service';
import { AuthenticatedUser } from '../auth/auth.model';
import { HeaderComponent } from './header.component';

@Component({ template: '' })
class RouteStubComponent {}

describe('HeaderComponent', () => {
  let fixture: ComponentFixture<HeaderComponent>;
  let auth: {
    currentUser: ReturnType<typeof signal<AuthenticatedUser | null>>;
    logout: ReturnType<typeof vi.fn>;
    clearSession: ReturnType<typeof vi.fn>;
  };

  beforeEach(async () => {
    auth = {
      currentUser: signal({
        authenticated: true,
        userId: 1,
        username: 'coach',
        role: 'COACH',
        athleteId: null,
      }),
      logout: vi.fn(() => of(undefined)),
      clearSession: vi.fn(),
    };
    await TestBed.configureTestingModule({
      imports: [HeaderComponent],
      providers: [
        provideRouter([
          { path: 'login', component: RouteStubComponent },
          { path: 'dashboard', component: RouteStubComponent },
          { path: 'tests', component: RouteStubComponent },
          { path: 'competition-events', component: RouteStubComponent },
          { path: 'accounts', component: RouteStubComponent },
          { path: 'athletes/:id', component: RouteStubComponent },
          { path: 'athletes', component: RouteStubComponent },
          { path: 'results', component: RouteStubComponent },
          { path: 'trainings/:id', component: RouteStubComponent },
          { path: 'trainings', component: RouteStubComponent },
        ]),
        { provide: AuthService, useValue: auth },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(HeaderComponent);
    fixture.detectChanges();
  });

  it('shows the selected TFHub banner image as the home link branding', () => {
    const brand = fixture.nativeElement.querySelector('.brand') as HTMLAnchorElement;
    const image = brand.querySelector('img') as HTMLImageElement;
    expect(brand.getAttribute('aria-label')).toBe('TFHUB — strona główna');
    expect(image.getAttribute('src')).toBe('/tfhub-banner.png');
    expect(image.getAttribute('alt')).toBe('TFHub');
  });

  it('hides navigation while signed out and restores it closed after signing in', () => {
    const coach = auth.currentUser();
    fixture.nativeElement.querySelector('.navigation-toggle').click();
    fixture.detectChanges();

    auth.currentUser.set(null);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('nav')).toBeNull();
    expect(fixture.nativeElement.querySelector('.navigation-toggle')).toBeNull();
    expect(fixture.nativeElement.querySelector('.brand img')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('.login-link')?.textContent).toContain('Zaloguj');
    expect(fixture.nativeElement.textContent).not.toContain('Dashboard');

    auth.currentUser.set(coach);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('nav a[href="/dashboard"]')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('.navigation-toggle').getAttribute('aria-expanded')).toBe('false');
  });

  it('opens and closes the account menu when the username is clicked', () => {
    const username = fixture.nativeElement.querySelector('.username') as HTMLButtonElement;

    username.click();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('#user-menu')).toBeTruthy();
    expect(username.getAttribute('aria-expanded')).toBe('true');

    username.click();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('#user-menu')).toBeNull();
  });

  it('toggles mobile navigation and closes it on link selection, Escape and outside click', () => {
    const button = fixture.nativeElement.querySelector('.navigation-toggle') as HTMLButtonElement;
    const nav = fixture.nativeElement.querySelector('nav') as HTMLElement;
    expect(button.getAttribute('aria-expanded')).toBe('false');
    expect(nav.classList.contains('navigation-open')).toBe(false);
    button.click();
    fixture.detectChanges();
    expect(button.getAttribute('aria-expanded')).toBe('true');
    expect(nav.classList.contains('navigation-open')).toBe(true);
    (nav.querySelector('a') as HTMLAnchorElement).click();
    fixture.detectChanges();
    expect(button.getAttribute('aria-expanded')).toBe('false');
    button.click();
    const focus = vi.spyOn(button, 'focus');
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    fixture.detectChanges();
    expect(button.getAttribute('aria-expanded')).toBe('false');
    expect(focus).toHaveBeenCalledOnce();
    button.click();
    document.body.click();
    fixture.detectChanges();
    expect(button.getAttribute('aria-expanded')).toBe('false');
  });

  it('keeps account and navigation menus mutually exclusive', () => {
    const navigationButton = fixture.nativeElement.querySelector('.navigation-toggle') as HTMLButtonElement;
    const username = fixture.nativeElement.querySelector('.username') as HTMLButtonElement;
    navigationButton.click();
    username.click();
    fixture.detectChanges();
    expect(navigationButton.getAttribute('aria-expanded')).toBe('false');
    expect(username.getAttribute('aria-expanded')).toBe('true');
    navigationButton.click();
    fixture.detectChanges();
    expect(navigationButton.getAttribute('aria-expanded')).toBe('true');
    expect(username.getAttribute('aria-expanded')).toBe('false');
  });

  it('shows configuration only for administrators and keeps account actions in the user menu', () => {
    expect(fixture.nativeElement.textContent).not.toContain('Zarządzanie kontami');

    auth.currentUser.set({
      authenticated: true,
      userId: 1,
      username: 'admin',
      role: 'ADMIN',
      athleteId: null,
    });

    fixture.detectChanges();

    const link = fixture.nativeElement.querySelector('nav a[href="/accounts"]') as HTMLAnchorElement;
    expect(link.textContent?.trim()).toBe('Zarządzanie kontami');
    fixture.nativeElement.querySelector('.username').click();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('#user-menu a[href="/accounts"]')).toBeNull();
    expect(Array.from(fixture.nativeElement.querySelectorAll('#user-menu button') as NodeListOf<HTMLButtonElement>)
      .map((button) => button.textContent?.trim())).toEqual(['Zmiana hasła', 'Wyloguj']);

    auth.currentUser.set({
      authenticated: true,
      userId: 1,
      username: 'coach',
      role: 'COACH',
      athleteId: null,
    });
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('nav a[href="/accounts"]')).toBeNull();
  });

  it('shows the training navigation link to staff accounts', () => {
    expect(fixture.nativeElement.querySelector('nav a[href="/trainings"]')?.textContent).toContain(
      'Treningi',
    );

    auth.currentUser.set({
      authenticated: true,
      userId: 1,
      username: 'athlete',
      role: 'ATHLETE',
      athleteId: 2,
    });
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('nav a[href="/trainings"]')).toBeNull();
  });

  it('renders the approved order for staff and preserves the athlete profile link', () => {
    const navigationLabels = () => Array.from(
      fixture.nativeElement.querySelectorAll('nav > a, nav > .management-menu > button') as NodeListOf<HTMLElement>,
    ).map((item) => item.textContent?.trim().replace(/\s*▾$/, ''));

    expect(navigationLabels()).toEqual(['Dashboard', 'Zawodnicy', 'Treningi', 'Wyniki']);
    auth.currentUser.update((user) => user && { ...user, role: 'ADMIN' });
    fixture.detectChanges();
    expect(navigationLabels()).toEqual(['Dashboard', 'Zawodnicy', 'Treningi', 'Wyniki', 'Konfiguracja']);
    expect(Array.from(fixture.nativeElement.querySelectorAll('.management-links a') as NodeListOf<HTMLAnchorElement>)
      .map((link) => [link.textContent?.trim(), link.getAttribute('href')])).toEqual([
      ['Definicje sprawdzianów', '/tests'],
      ['Konkurencje PZLA', '/competition-events'],
      ['Zarządzanie kontami', '/accounts'],
    ]);

    auth.currentUser.update((user) => user && { ...user, role: 'ATHLETE', athleteId: 2 });
    fixture.detectChanges();
    expect(navigationLabels()).toEqual(['Moje wyniki']);
    expect(fixture.nativeElement.querySelector('nav a').getAttribute('href')).toBe('/athletes/2');
    expect(fixture.nativeElement.querySelector('.management-menu')).toBeNull();
  });

  it.each(['/tests', '/competition-events', '/accounts'])(
    'marks configuration and its current link active for %s',
    async (url) => {
      auth.currentUser.update((user) => user && { ...user, role: 'ADMIN' });
      fixture.detectChanges();
      await TestBed.inject(Router).navigateByUrl(url);
      fixture.detectChanges();
      await fixture.whenStable();
      fixture.detectChanges();
      expect(fixture.nativeElement.querySelector('.management-menu').classList.contains('active')).toBe(true);
      const link = fixture.nativeElement.querySelector(`.management-links a[href="${url}"]`) as HTMLAnchorElement;
      expect(link.classList.contains('active')).toBe(true);
      expect(link.getAttribute('aria-current')).toBe('page');
    },
  );

  it('closes the innermost open mobile navigation level on Escape and restores focus', () => {
    auth.currentUser.update((user) => user && { ...user, role: 'ADMIN' });
    fixture.detectChanges();
    const navigation = fixture.nativeElement.querySelector('.navigation-toggle') as HTMLButtonElement;
    const management = fixture.nativeElement.querySelector('.management-toggle') as HTMLButtonElement;
    navigation.click();
    management.click();
    fixture.detectChanges();
    const focusManagement = vi.spyOn(management, 'focus');
    const focusNavigation = vi.spyOn(navigation, 'focus');
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    fixture.detectChanges();
    expect(management.getAttribute('aria-expanded')).toBe('false');
    expect(navigation.getAttribute('aria-expanded')).toBe('true');
    expect(focusManagement).toHaveBeenCalledOnce();
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    fixture.detectChanges();
    expect(navigation.getAttribute('aria-expanded')).toBe('false');
    expect(focusNavigation).toHaveBeenCalledOnce();
  });

  it('closes the user disclosure on Escape and restores focus to its button', () => {
    const username = fixture.nativeElement.querySelector('.username') as HTMLButtonElement;
    username.click();
    fixture.detectChanges();
    expect(username.getAttribute('aria-controls')).toBe('user-menu');
    const focus = vi.spyOn(username, 'focus');
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    fixture.detectChanges();
    expect(username.getAttribute('aria-expanded')).toBe('false');
    expect(fixture.nativeElement.querySelector('#user-menu')).toBeNull();
    expect(focus).toHaveBeenCalledOnce();
  });

  it('shows competition discipline management only to administrators', () => {
    expect(fixture.nativeElement.querySelector('nav a[href="/competition-events"]')).toBeNull();
    auth.currentUser.set({
      authenticated: true,
      userId: 1,
      username: 'admin',
      role: 'ADMIN',
      athleteId: null,
    });
    fixture.detectChanges();
    const management = fixture.nativeElement.querySelector('.management-toggle') as HTMLButtonElement;
    expect(management.textContent).toContain('Konfiguracja');
    expect(management.getAttribute('aria-expanded')).toBe('false');
    management.click();
    fixture.detectChanges();
    expect(management.getAttribute('aria-expanded')).toBe('true');
    expect(fixture.nativeElement.querySelector('nav a[href="/tests"]')?.textContent).toContain('Definicje sprawdzianów');
    expect(fixture.nativeElement.querySelector('nav a[href="/competition-events"]')?.textContent)
      .toContain('Konkurencje PZLA');
    const focus = vi.spyOn(management, 'focus');
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    fixture.detectChanges();
    expect(management.getAttribute('aria-expanded')).toBe('false');
    expect(focus).toHaveBeenCalledOnce();
    management.click();
    fixture.detectChanges();
    document.body.click();
    fixture.detectChanges();
    expect(management.getAttribute('aria-expanded')).toBe('false');

    auth.currentUser.set({
      authenticated: true,
      userId: 1,
      username: 'coach',
      role: 'COACH',
      athleteId: null,
    });
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('nav a[href="/competition-events"]')).toBeNull();
    expect(fixture.nativeElement.querySelector('.management-toggle')).toBeNull();
  });

  it.each(['/tests', '/competition-events', '/accounts'])(
    'closes both navigation levels after selecting %s on mobile',
    (url) => {
      auth.currentUser.update((user) => user && { ...user, role: 'ADMIN' });
      fixture.detectChanges();
      const navigation = fixture.nativeElement.querySelector('.navigation-toggle') as HTMLButtonElement;
      const management = fixture.nativeElement.querySelector('.management-toggle') as HTMLButtonElement;
      navigation.click();
      management.click();
      fixture.detectChanges();
      expect(navigation.getAttribute('aria-expanded')).toBe('true');
      expect(management.getAttribute('aria-expanded')).toBe('true');
      (fixture.nativeElement.querySelector(`.management-links a[href="${url}"]`) as HTMLAnchorElement).click();
      fixture.detectChanges();
      expect(navigation.getAttribute('aria-expanded')).toBe('false');
      expect(management.getAttribute('aria-expanded')).toBe('false');
    },
  );

  it('closes the menu and opens the change-password dialog when selected', () => {
    fixture.nativeElement.querySelector('.username').click();
    fixture.detectChanges();
    fixture.nativeElement.querySelector('#user-menu button').click();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('#user-menu')).toBeNull();
    expect(fixture.nativeElement.querySelector('app-change-password-modal')).toBeTruthy();
    expect(fixture.nativeElement.textContent).toContain('Zmień hasło');
  });

  it('closes the password dialog when cancelled', () => {
    fixture.nativeElement.querySelector('.username').click();
    fixture.detectChanges();
    fixture.nativeElement.querySelector('#user-menu button').click();
    fixture.detectChanges();

    fixture.nativeElement.querySelector('.cancel-button').click();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('app-change-password-modal')).toBeNull();
  });

  it('preserves the logout action and closes the menu before logging out', async () => {
    fixture.nativeElement.querySelector('.username').click();
    fixture.detectChanges();
    fixture.nativeElement.querySelectorAll('#user-menu button')[1].click();
    fixture.detectChanges();
    await fixture.whenStable();

    expect(auth.logout).toHaveBeenCalledOnce();
    expect(fixture.nativeElement.querySelector('#user-menu')).toBeNull();
    expect(TestBed.inject(Router).url).toBe('/login');
  });

  it('shows success feedback, clears the frontend session and returns to login after a password change', async () => {
    fixture.componentInstance.onPasswordChanged();
    fixture.detectChanges();
    await fixture.whenStable();

    expect(auth.clearSession).toHaveBeenCalledOnce();
    expect(fixture.nativeElement.querySelector('[role="status"]').textContent).toContain('Hasło zostało zmienione.');
    expect(TestBed.inject(Router).url).toBe('/login');

    fixture.componentInstance.dismissSuccessMessage();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[role="status"]')).toBeNull();
  });
});

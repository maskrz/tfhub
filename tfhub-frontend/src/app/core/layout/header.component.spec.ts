import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { of } from 'rxjs';
import { signal } from '@angular/core';
import { AuthService } from '../auth/auth.service';
import { AuthenticatedUser } from '../auth/auth.model';
import { HeaderComponent } from './header.component';

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
        provideRouter([{ path: 'login', children: [] }]),
        { provide: AuthService, useValue: auth },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(HeaderComponent);
    fixture.detectChanges();
  });

  it('opens and closes the account menu when the username is clicked', () => {
    const username = fixture.nativeElement.querySelector('.username') as HTMLButtonElement;

    username.click();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[role="menu"]')).toBeTruthy();
    expect(username.getAttribute('aria-expanded')).toBe('true');

    username.click();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[role="menu"]')).toBeNull();
  });

  it('closes the menu and opens the change-password dialog when selected', () => {
    fixture.nativeElement.querySelector('.username').click();
    fixture.detectChanges();
    fixture.nativeElement.querySelector('[role="menuitem"]').click();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[role="menu"]')).toBeNull();
    expect(fixture.nativeElement.querySelector('app-change-password-modal')).toBeTruthy();
    expect(fixture.nativeElement.textContent).toContain('Zmień hasło');
  });

  it('closes the password dialog when cancelled', () => {
    fixture.nativeElement.querySelector('.username').click();
    fixture.detectChanges();
    fixture.nativeElement.querySelector('[role="menuitem"]').click();
    fixture.detectChanges();

    fixture.nativeElement.querySelector('.cancel-button').click();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('app-change-password-modal')).toBeNull();
  });

  it('preserves the logout action and closes the menu before logging out', async () => {
    fixture.nativeElement.querySelector('.username').click();
    fixture.detectChanges();
    fixture.nativeElement.querySelectorAll('[role="menuitem"]')[1].click();
    fixture.detectChanges();
    await fixture.whenStable();

    expect(auth.logout).toHaveBeenCalledOnce();
    expect(fixture.nativeElement.querySelector('[role="menu"]')).toBeNull();
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

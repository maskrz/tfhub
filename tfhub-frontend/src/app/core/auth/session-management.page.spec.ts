import { provideRouter, Router } from '@angular/router';
import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { AuthService } from './auth.service';
import { SessionManagementPage } from './session-management.page';

const currentSession = {
  id: 'current-handle',
  createdAt: '2026-11-20T10:00:00Z',
  lastActivityAt: '2026-11-24T10:00:00Z',
  current: true,
};
const otherSession = {
  id: 'other-handle',
  createdAt: '2026-11-19T10:00:00Z',
  lastActivityAt: '2026-11-23T10:00:00Z',
  current: false,
};

describe('SessionManagementPage', () => {
  it('displays the current and other sessions and refreshes after revoking one', async () => {
    const auth = {
      getSessions: vi.fn()
        .mockReturnValueOnce(of([currentSession, otherSession]))
        .mockReturnValueOnce(of([currentSession])),
      revokeSession: vi.fn(() => of(void 0)),
      clearSession: vi.fn(),
    };
    await TestBed.configureTestingModule({
      imports: [SessionManagementPage],
      providers: [provideRouter([]), { provide: AuthService, useValue: auth }],
    }).compileComponents();

    const fixture = TestBed.createComponent(SessionManagementPage);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Bieżąca sesja');
    expect(fixture.nativeElement.textContent).toContain('Utworzono:');
    expect(fixture.nativeElement.textContent).toContain('Ostatnia aktywność:');
    expect(fixture.nativeElement.textContent).not.toContain('current-handle');

    const revokeButton = fixture.nativeElement.querySelectorAll('button.button--danger')[1] as HTMLButtonElement;
    revokeButton.click();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(auth.revokeSession).toHaveBeenCalledWith('other-handle');
    expect(auth.revokeSession).not.toHaveBeenCalledWith('current-handle');
    expect(auth.getSessions).toHaveBeenCalledTimes(2);
  });

  it('clears local authentication and navigates to login after revoking the current session', async () => {
    const auth = {
      getSessions: vi.fn(() => of([currentSession])),
      revokeSession: vi.fn(() => of(void 0)),
      clearSession: vi.fn(),
    };
    await TestBed.configureTestingModule({
      imports: [SessionManagementPage],
      providers: [provideRouter([]), { provide: AuthService, useValue: auth }],
    }).compileComponents();
    const router = TestBed.inject(Router);
    const navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);

    const fixture = TestBed.createComponent(SessionManagementPage);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    (fixture.nativeElement.querySelector('button.button--danger') as HTMLButtonElement).click();
    await fixture.whenStable();

    expect(auth.clearSession).toHaveBeenCalledOnce();
    expect(navigate).toHaveBeenCalledWith(['/login']);
  });

  it('shows an error when sessions cannot be loaded', async () => {
    const auth = {
      getSessions: vi.fn(() => throwError(() => new Error('network error'))),
      revokeSession: vi.fn(),
      clearSession: vi.fn(),
    };
    await TestBed.configureTestingModule({
      imports: [SessionManagementPage],
      providers: [provideRouter([]), { provide: AuthService, useValue: auth }],
    }).compileComponents();

    const fixture = TestBed.createComponent(SessionManagementPage);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[role="alert"]')?.textContent).toContain('Nie udało się wczytać sesji');
  });

  it('shows an error when a revoke request fails', async () => {
    const auth = {
      getSessions: vi.fn(() => of([otherSession])),
      revokeSession: vi.fn(() => throwError(() => new Error('network error'))),
      clearSession: vi.fn(),
    };
    await TestBed.configureTestingModule({
      imports: [SessionManagementPage],
      providers: [provideRouter([]), { provide: AuthService, useValue: auth }],
    }).compileComponents();

    const fixture = TestBed.createComponent(SessionManagementPage);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    (fixture.nativeElement.querySelector('button.button--danger') as HTMLButtonElement).click();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[role="alert"]')?.textContent).toContain('Nie udało się zakończyć sesji');
  });
});

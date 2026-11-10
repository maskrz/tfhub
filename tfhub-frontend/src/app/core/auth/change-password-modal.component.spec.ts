import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, Subject, throwError } from 'rxjs';
import { AuthService } from './auth.service';
import { ChangePasswordModalComponent } from './change-password-modal.component';

describe('ChangePasswordModalComponent', () => {
  let fixture: ComponentFixture<ChangePasswordModalComponent>;
  let auth: { changePassword: ReturnType<typeof vi.fn> };

  beforeEach(async () => {
    auth = { changePassword: vi.fn() };
    await TestBed.configureTestingModule({
      imports: [ChangePasswordModalComponent],
      providers: [{ provide: AuthService, useValue: auth }],
    }).compileComponents();
  });

  function createModal(): ChangePasswordModalComponent {
    fixture = TestBed.createComponent(ChangePasswordModalComponent);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  function enterPasswords(
    component: ChangePasswordModalComponent,
    currentPassword = 'current-secret',
    newPassword = 'new-secret',
    confirmation = newPassword,
  ): void {
    component.form.setValue({ currentPassword, newPassword, passwordConfirmation: confirmation });
    fixture.detectChanges();
  }

  it('renders the required password fields and localized labels', () => {
    createModal();

    const inputs = fixture.nativeElement.querySelectorAll('input');
    expect(inputs.length).toBe(3);
    expect(Array.from(inputs).every((input) => (input as HTMLInputElement).type === 'password')).toBe(true);
    expect(fixture.nativeElement.textContent).toContain('Aktualne hasło');
    expect(fixture.nativeElement.textContent).toContain('Nowe hasło');
    expect(fixture.nativeElement.textContent).toContain('Powtórz nowe hasło');
  });

  it('rejects required fields and mismatched confirmation without calling the API', () => {
    const component = createModal();

    component.submit();
    fixture.detectChanges();
    expect(component.form.controls.currentPassword.hasError('required')).toBe(true);
    expect(fixture.nativeElement.textContent).toContain('To pole jest wymagane.');

    enterPasswords(component, '   ', 'new-secret', 'new-secret');
    expect(component.form.controls.currentPassword.hasError('required')).toBe(true);

    enterPasswords(component, 'current-secret', 'new-secret', 'different-secret');
    expect(component.form.hasError('passwordsMismatch')).toBe(true);
    expect(fixture.nativeElement.textContent).toContain('Nowe hasła nie są zgodne.');
    expect(fixture.nativeElement.querySelector('.save-button').disabled).toBe(true);
    expect(auth.changePassword).not.toHaveBeenCalled();
  });

  it('submits the passwords and emits success after a successful response', () => {
    const component = createModal();
    const changed = vi.fn();
    component.changed.subscribe(changed);
    enterPasswords(component);
    auth.changePassword.mockReturnValue(of(undefined));

    component.submit();

    expect(auth.changePassword).toHaveBeenCalledOnce();
    expect(auth.changePassword).toHaveBeenCalledWith('current-secret', 'new-secret');
    expect(changed).toHaveBeenCalledOnce();
    expect(component.form.getRawValue()).toEqual({
      currentPassword: '',
      newPassword: '',
      passwordConfirmation: '',
    });
  });

  it('shows a useful Polish message for a rejected password without backend details', () => {
    const component = createModal();
    enterPasswords(component);
    auth.changePassword.mockReturnValue(throwError(() => new HttpErrorResponse({
      status: 400,
      error: { detail: 'internal backend detail' },
    })));

    component.submit();
    fixture.detectChanges();

    expect(component.errorMessage()).toContain('Sprawdź aktualne hasło');
    expect(fixture.nativeElement.textContent).not.toContain('internal backend detail');
    expect(fixture.nativeElement.querySelector('dialog')).toBeTruthy();
  });

  it('keeps the dialog open and shows standard feedback for other errors', () => {
    const component = createModal();
    enterPasswords(component);
    auth.changePassword.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));

    component.submit();
    fixture.detectChanges();

    expect(component.errorMessage()).toBe('Nie udało się zmienić hasła. Spróbuj ponownie.');
    expect(fixture.nativeElement.querySelector('dialog')).toBeTruthy();
  });

  it('closes on an expired-session response so the existing auth redirect can proceed', () => {
    const component = createModal();
    const cancelled = vi.fn();
    component.cancelled.subscribe(cancelled);
    enterPasswords(component);
    auth.changePassword.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 401 })));

    component.submit();

    expect(cancelled).toHaveBeenCalledOnce();
  });

  it('prevents duplicate submissions while the request is pending', () => {
    const component = createModal();
    const response = new Subject<void>();
    enterPasswords(component);
    auth.changePassword.mockReturnValue(response);

    component.submit();
    component.submit();
    fixture.detectChanges();

    expect(auth.changePassword).toHaveBeenCalledOnce();
    expect(component.saving()).toBe(true);
    expect(fixture.nativeElement.querySelector('.save-button').disabled).toBe(true);

    response.next();
    response.complete();
    fixture.detectChanges();
    expect(component.saving()).toBe(false);
  });

  it('cancels the dialog and clears all entered values', () => {
    const component = createModal();
    const cancelled = vi.fn();
    component.cancelled.subscribe(cancelled);
    enterPasswords(component);

    component.cancel();
    fixture.detectChanges();

    expect(cancelled).toHaveBeenCalledOnce();
    expect(component.form.getRawValue()).toEqual({
      currentPassword: '',
      newPassword: '',
      passwordConfirmation: '',
    });
  });
});

import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, Subject } from 'rxjs';
import { AuthService } from './auth.service';
import { LoginPage } from './login.page';

describe('LoginPage shared controls', () => {
  it('uses shared field and primary button styles while preserving validation and loading', async () => {
    const csrf = new Subject<void>();
    const auth = {
      ensureSession: vi.fn(() => of(null)),
      refreshCsrfToken: vi.fn(() => csrf),
      login: vi.fn(),
    };
    await TestBed.configureTestingModule({
      imports: [LoginPage],
      providers: [provideRouter([]), { provide: AuthService, useValue: auth }],
    }).compileComponents();

    const fixture = TestBed.createComponent(LoginPage);
    fixture.detectChanges();

    const inputs = fixture.nativeElement.querySelectorAll('input') as NodeListOf<HTMLInputElement>;
    const submit = fixture.nativeElement.querySelector('button[type="submit"]') as HTMLButtonElement;
    expect(Array.from(inputs).every((input) => input.classList.contains('field-control'))).toBe(true);
    expect(submit.classList.contains('button--primary')).toBe(true);
    expect(submit.disabled).toBe(true);

    fixture.componentInstance.form.setValue({ username: 'coach', password: 'password' });
    fixture.detectChanges();
    expect(submit.disabled).toBe(false);
    submit.click();
    fixture.detectChanges();
    expect(submit.disabled).toBe(true);
    expect(submit.textContent).toContain('Logowanie');
    expect(auth.refreshCsrfToken).toHaveBeenCalledOnce();
    expect(auth.login).not.toHaveBeenCalled();
    csrf.complete();
  });
});

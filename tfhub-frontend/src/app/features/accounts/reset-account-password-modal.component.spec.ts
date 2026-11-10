import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, Subject } from 'rxjs';
import { AccountResponse } from './account.model';
import { AccountsApiService } from './accounts-api.service';
import { ResetAccountPasswordModalComponent } from './reset-account-password-modal.component';

describe('ResetAccountPasswordModalComponent', () => {
  let fixture: ComponentFixture<ResetAccountPasswordModalComponent>;
  let api: { resetPassword: ReturnType<typeof vi.fn> };
  const account: AccountResponse = {
    id: 7, username: 'zawodnik', role: 'ATHLETE', active: true, trainingGroups: [], athlete: null,
  };

  beforeEach(async () => {
    api = { resetPassword: vi.fn(() => of(undefined)) };
    await TestBed.configureTestingModule({
      imports: [ResetAccountPasswordModalComponent],
      providers: [{ provide: AccountsApiService, useValue: api }],
    }).compileComponents();
    fixture = TestBed.createComponent(ResetAccountPasswordModalComponent);
    fixture.componentRef.setInput('account', account);
    fixture.detectChanges();
  });

  it('requires both password fields and validates that values match', () => {
    const component = fixture.componentInstance;
    component.form.controls.newPassword.setValue('new-password');
    component.form.controls.repeatPassword.setValue('different');
    component.submit();
    fixture.detectChanges();

    expect(api.resetPassword).not.toHaveBeenCalled();
    expect(component.fieldError('repeatPassword')).toBe('Hasła muszą być identyczne.');
    expect(fixture.nativeElement.querySelectorAll('input[type="password"]').length).toBe(2);
  });

  it('resets the account password with the newPassword DTO and clears values on success', () => {
    const reset = vi.fn();
    fixture.componentInstance.reset.subscribe(reset);
    fixture.componentInstance.form.setValue({ newPassword: 'new-password', repeatPassword: 'new-password' });
    fixture.componentInstance.submit();

    expect(api.resetPassword).toHaveBeenCalledWith(7, { newPassword: 'new-password' });
    expect(reset).toHaveBeenCalledOnce();
    expect(fixture.componentInstance.form.value).toEqual({ newPassword: '', repeatPassword: '' });
  });

  it('blocks duplicate password reset submissions while a request is pending', () => {
    const pending = new Subject<void>();
    api.resetPassword.mockReturnValue(pending);
    fixture.componentInstance.form.setValue({ newPassword: 'same', repeatPassword: 'same' });
    fixture.componentInstance.submit();
    fixture.componentInstance.submit();

    expect(api.resetPassword).toHaveBeenCalledOnce();
    expect(fixture.componentInstance.saving()).toBe(true);
    pending.next();
    pending.complete();
    expect(fixture.componentInstance.saving()).toBe(false);
  });
});

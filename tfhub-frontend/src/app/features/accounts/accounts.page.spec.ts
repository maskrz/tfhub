import { HttpErrorResponse } from '@angular/common/http';
import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { of, Subject, throwError } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { AccountResponse } from './account.model';
import { AccountsApiService } from './accounts-api.service';
import { AccountsPage } from './accounts.page';

describe('AccountsPage', () => {
  let fixture: ComponentFixture<AccountsPage>;
  let api: {
    getAll: ReturnType<typeof vi.fn>;
    changeStatus: ReturnType<typeof vi.fn>;
  };
  let auth: {
    currentUser: ReturnType<typeof signal<{ userId: number; username: string; role: 'ADMIN'; authenticated: true; athleteId: null } | null>>;
    clearSession: ReturnType<typeof vi.fn>;
  };
  let router: { navigate: ReturnType<typeof vi.fn> };
  const accounts: AccountResponse[] = [
    { id: 1, username: 'admin', role: 'ADMIN', active: true, trainingGroups: [], athlete: null },
    { id: 2, username: 'coach', role: 'COACH', active: true, trainingGroups: [{ id: 3, name: 'Sprint' }], athlete: null },
    {
      id: 4, username: 'athlete', role: 'ATHLETE', active: false, trainingGroups: [],
      athlete: { id: 5, firstName: 'Anna', lastName: 'Nowak', birthYear: 2005, trainingGroupId: 3, trainingGroupName: 'Sprint' },
    },
  ];

  beforeEach(async () => {
    api = {
      getAll: vi.fn(() => of(accounts)),
      changeStatus: vi.fn(() => of({ ...accounts[2], active: true })),
    };
    auth = {
      currentUser: signal({ userId: 1, username: 'admin', role: 'ADMIN', authenticated: true, athleteId: null }),
      clearSession: vi.fn(),
    };
    router = { navigate: vi.fn() };
    await TestBed.configureTestingModule({
      imports: [AccountsPage],
      providers: [
        { provide: AccountsApiService, useValue: api },
        { provide: AuthService, useValue: auth },
        { provide: Router, useValue: router },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(AccountsPage);
    fixture.detectChanges();
  });

  it('loads and displays account roles, statuses, and role-specific assignments', () => {
    expect(api.getAll).toHaveBeenCalledOnce();
    expect(fixture.nativeElement.textContent).toContain('Sprint');
    expect(fixture.nativeElement.textContent).toContain('Anna Nowak (2005)');
    expect(fixture.nativeElement.textContent).toContain('Administrator');
    expect(fixture.nativeElement.textContent).toContain('Nieaktywne');
  });

  it('filters the full account list locally without another backend request', () => {
    fixture.componentInstance.usernameFilter.set('COA');
    fixture.componentInstance.roleFilter.set('COACH');
    fixture.componentInstance.activeFilter.set('false');
    fixture.detectChanges();

    expect(api.getAll).toHaveBeenCalledOnce();
    expect(fixture.componentInstance.visibleAccounts()).toEqual([]);

    fixture.componentInstance.activeFilter.set('true');
    expect(fixture.componentInstance.visibleAccounts().map((account) => account.username)).toEqual(['coach']);
  });

  it('updates account filters immediately as the user types or changes selections', () => {
    const input = fixture.nativeElement.querySelector('[aria-label="Filtruj według nazwy użytkownika"]') as HTMLInputElement;
    input.value = 'ath';
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelectorAll('tbody tr')).toHaveLength(1);
    expect(fixture.nativeElement.querySelector('tbody').textContent).toContain('athlete');
    expect(fixture.nativeElement.textContent).not.toContain('Filtruj</button>');
    expect(fixture.nativeElement.textContent).not.toContain('Wyczyść');
  });

  it('requires confirmation before deactivation and refreshes after status changes succeed', () => {
    fixture.componentInstance.askToDeactivate(accounts[1]);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Dezaktywować konto „coach”?');
    expect(api.changeStatus).not.toHaveBeenCalled();

    fixture.componentInstance.confirmDeactivation();
    expect(api.changeStatus).toHaveBeenCalledWith(2, { active: false });
    expect(fixture.componentInstance.pendingDeactivation()).toBeNull();

    fixture.componentInstance.activate(accounts[2]);
    expect(api.changeStatus).toHaveBeenCalledWith(4, { active: true });
    expect(api.getAll).toHaveBeenCalledTimes(3);
    expect(fixture.componentInstance.successMessage()).toContain('aktywowane');
  });

  it('translates backend protection when deactivation would remove the last active ADMIN', () => {
    api.changeStatus.mockReturnValue(throwError(() => new HttpErrorResponse({
      status: 409,
      error: { title: 'Administrator required', detail: 'The system must have at least one active ADMIN account' },
    })));
    fixture.componentInstance.askToDeactivate(accounts[0]);
    fixture.componentInstance.confirmDeactivation();

    expect(fixture.componentInstance.statusError()).toBe(
      'Nie można dezaktywować ostatniego aktywnego administratora.',
    );
    expect(api.getAll).toHaveBeenCalledOnce();
  });

  it('shows loading and error feedback when list requests fail', () => {
    api.getAll.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
    fixture.componentInstance.retry();
    fixture.detectChanges();

    expect(fixture.componentInstance.errorMessage()).toContain('Nie udało się pobrać listy kont');
    expect(fixture.nativeElement.querySelector('[role="alert"]')).toBeTruthy();
  });

  it('returns to login after resetting the current administrator password', () => {
    fixture.componentInstance.resetAccount.set(accounts[0]);
    fixture.componentInstance.onPasswordReset();

    expect(auth.clearSession).toHaveBeenCalledOnce();
    expect(router.navigate).toHaveBeenCalledWith(
      ['/login'],
      { queryParams: { notice: 'account-password-reset' } },
    );
    expect(fixture.componentInstance.resetAccount()).toBeNull();
  });

  it('shows a loading state until the backend list response arrives', () => {
    const pending = new Subject<AccountResponse[]>();
    api.getAll.mockReturnValue(pending);
    fixture.componentInstance.retry();
    fixture.detectChanges();
    expect(fixture.componentInstance.loading()).toBe(true);
    expect(fixture.nativeElement.querySelector('[role="status"]').textContent).toContain('Ładowanie kont');

    pending.next(accounts);
    pending.complete();
    fixture.detectChanges();
    expect(fixture.componentInstance.loading()).toBe(false);
  });
});

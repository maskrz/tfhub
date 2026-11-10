import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Router } from '@angular/router';
import { EMPTY, catchError, finalize, take } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { UserRole } from '../../core/auth/auth.model';
import { AccountResponse } from './account.model';
import { AccountsApiService } from './accounts-api.service';
import { AccountFormModalComponent } from './account-form-modal.component';
import { AccountFormMode } from './account-form.component';
import { ResetAccountPasswordModalComponent } from './reset-account-password-modal.component';

@Component({
  imports: [AccountFormModalComponent, ResetAccountPasswordModalComponent],
  selector: 'app-accounts-page',
  templateUrl: './accounts.page.html',
  styleUrl: './accounts.page.scss',
})
export class AccountsPage implements OnInit {
  readonly accounts = signal<AccountResponse[]>([]);
  readonly loading = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly statusError = signal<string | null>(null);
  readonly successMessage = signal<string | null>(null);
  readonly usernameFilter = signal('');
  readonly roleFilter = signal<UserRole | ''>('');
  readonly activeFilter = signal<'' | 'true' | 'false'>('');
  readonly formOpen = signal(false);
  readonly formMode = signal<AccountFormMode>('CREATE');
  readonly selectedAccount = signal<AccountResponse | null>(null);
  readonly resetAccount = signal<AccountResponse | null>(null);
  readonly pendingDeactivation = signal<AccountResponse | null>(null);
  readonly statusUpdatingId = signal<number | null>(null);
  readonly visibleAccounts = computed(() => {
    const username = this.usernameFilter().trim().toLocaleLowerCase('pl');
    const role = this.roleFilter();
    const active = this.activeFilter();

    return this.accounts().filter((account) => {
      if (username && !account.username.toLocaleLowerCase('pl').includes(username)) return false;
      if (role && account.role !== role) return false;
      if (active && account.active !== (active === 'true')) return false;
      return true;
    });
  });

  private readonly api = inject(AccountsApiService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  ngOnInit(): void {
    this.loadAccounts();
  }

  onUsernameInput(event: Event): void {
    const input = event.target;
    if (input instanceof HTMLInputElement) this.usernameFilter.set(input.value);
  }

  onRoleChange(event: Event): void {
    const select = event.target;
    if (!(select instanceof HTMLSelectElement)) return;
    const value = select.value;
    if (value === '' || value === 'ADMIN' || value === 'COACH' || value === 'ATHLETE') {
      this.roleFilter.set(value);
    }
  }

  onStatusChange(event: Event): void {
    const select = event.target;
    if (!(select instanceof HTMLSelectElement)) return;
    const value = select.value;
    if (value === '' || value === 'true' || value === 'false') {
      this.activeFilter.set(value);
    }
  }

  openCreateForm(): void {
    this.successMessage.set(null);
    this.formMode.set('CREATE');
    this.selectedAccount.set(null);
    this.formOpen.set(true);
  }

  openEditForm(account: AccountResponse): void {
    this.successMessage.set(null);
    this.formMode.set('EDIT');
    this.selectedAccount.set(account);
    this.formOpen.set(true);
  }

  openPasswordReset(account: AccountResponse): void {
    this.successMessage.set(null);
    this.resetAccount.set(account);
  }

  closeForm(): void {
    this.formOpen.set(false);
  }

  onAccountSaved(account: AccountResponse): void {
    const created = this.formMode() === 'CREATE';
    this.formOpen.set(false);
    this.successMessage.set(created ? 'Konto zostało utworzone.' : 'Zmiany konta zostały zapisane.');
    this.loadAccounts();
  }

  onPasswordReset(): void {
    const account = this.resetAccount();
    this.resetAccount.set(null);
    if (account && this.auth.currentUser()?.userId === account.id) {
      this.auth.clearSession();
      void this.router.navigate(['/login'], { queryParams: { notice: 'account-password-reset' } });
      return;
    }
    this.successMessage.set('Hasło zostało zmienione.');
  }

  askToDeactivate(account: AccountResponse): void {
    this.statusError.set(null);
    this.pendingDeactivation.set(account);
  }

  cancelDeactivation(): void {
    this.pendingDeactivation.set(null);
  }

  activate(account: AccountResponse): void {
    this.changeStatus(account, true);
  }

  confirmDeactivation(): void {
    const account = this.pendingDeactivation();
    if (account) this.changeStatus(account, false);
  }

  retry(): void {
    this.loadAccounts();
  }

  roleLabel(role: UserRole): string {
    switch (role) {
      case 'ADMIN': return 'Administrator';
      case 'COACH': return 'Trener';
      case 'ATHLETE': return 'Zawodnik';
    }
  }

  athleteName(account: AccountResponse): string {
    if (!account.athlete) return 'Brak przypisanego zawodnika';
    return `${account.athlete.firstName} ${account.athlete.lastName} (${account.athlete.birthYear})`;
  }

  trainingGroupNames(account: AccountResponse): string {
    return account.trainingGroups.length
      ? account.trainingGroups.map((group) => group.name).join(', ')
      : 'Brak grup';
  }

  private loadAccounts(): void {
    this.loading.set(true);
    this.errorMessage.set(null);
    this.api.getAll().pipe(
      take(1),
      catchError((error: unknown) => {
        this.accounts.set([]);
        this.errorMessage.set(this.getErrorMessage(error, 'list'));
        return EMPTY;
      }),
      finalize(() => this.loading.set(false)),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe((accounts) => this.accounts.set(accounts));
  }

  private changeStatus(account: AccountResponse, active: boolean): void {
    if (this.statusUpdatingId() !== null) return;
    this.statusError.set(null);
    this.statusUpdatingId.set(account.id);
    this.api.changeStatus(account.id, { active }).pipe(
      take(1),
      catchError((error: unknown) => {
        this.statusError.set(this.getErrorMessage(error, 'status'));
        return EMPTY;
      }),
      finalize(() => this.statusUpdatingId.set(null)),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe(() => {
      this.pendingDeactivation.set(null);
      this.successMessage.set(active ? 'Konto zostało aktywowane.' : 'Konto zostało dezaktywowane.');
      this.loadAccounts();
    });
  }

  private getErrorMessage(error: unknown, operation: 'list' | 'status'): string {
    if (!(error instanceof HttpErrorResponse)) return 'Wystąpił nieoczekiwany błąd. Spróbuj ponownie.';
    const body = error.error as { title?: string; detail?: string } | null;
    if (error.status === 401) return 'Sesja wygasła lub użytkownik nie jest zalogowany.';
    if (error.status === 403) return 'Brak uprawnień do zarządzania kontami.';
    if (operation === 'status' && error.status === 409
      && body?.title === 'Administrator required') {
      return 'Nie można dezaktywować ostatniego aktywnego administratora.';
    }
    if (error.status === 404) return 'Nie znaleziono konta.';
    return operation === 'list'
      ? 'Nie udało się pobrać listy kont. Spróbuj ponownie.'
      : 'Nie udało się zmienić statusu konta. Spróbuj ponownie.';
  }
}

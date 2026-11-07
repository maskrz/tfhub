import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { EMPTY, catchError, finalize } from 'rxjs';
import { TestResponse } from './test.model';
import { TestsApiService } from './tests-api.service';
import { TestFormModalComponent } from './test-form-modal.component';

@Component({
  imports: [TestFormModalComponent],
  selector: 'app-test-management-page',
  styleUrl: './test-management.page.scss',
  templateUrl: './test-management.page.html',
})
export class TestManagementPage implements OnInit {
  readonly allTests = signal<TestResponse[]>([]);
  readonly loading = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly query = signal('');
  readonly showInactive = signal(false);
  readonly formMode = signal<'CREATE' | 'EDIT'>('CREATE');
  readonly selectedTest = signal<TestResponse | null>(null);
  readonly isFormOpen = signal(false);
  readonly statusUpdatingId = signal<number | null>(null);
  readonly statusError = signal<string | null>(null);
  readonly visibleTests = computed(() => {
    const query = this.query().trim().toLowerCase();
    return this.allTests().filter(
      (test) =>
        (this.showInactive() || test.active) &&
        (!query || test.name.toLowerCase().includes(query)),
    );
  });

  private readonly api = inject(TestsApiService);
  private readonly destroyRef = inject(DestroyRef);

  ngOnInit(): void {
    this.loadTests();
  }

  onSearchInput(event: Event): void {
    const input = event.target;
    if (input instanceof HTMLInputElement) {
      this.query.set(input.value);
    }
  }

  onInactiveToggle(event: Event): void {
    const input = event.target;
    if (input instanceof HTMLInputElement) {
      this.showInactive.set(input.checked);
    }
  }

  retry(): void {
    this.loadTests();
  }

  openCreateForm(): void {
    this.formMode.set('CREATE');
    this.selectedTest.set(null);
    this.isFormOpen.set(true);
  }

  openEditForm(test: TestResponse): void {
    this.formMode.set('EDIT');
    this.selectedTest.set(test);
    this.isFormOpen.set(true);
  }

  closeForm(): void {
    this.isFormOpen.set(false);
  }

  onTestSaved(test: TestResponse): void {
    this.allTests.update((tests) => {
      const existingIndex = tests.findIndex((current) => current.id === test.id);
      if (existingIndex < 0) {
        return [...tests, test];
      }
      return tests.map((current) => (current.id === test.id ? test : current));
    });
    this.closeForm();
  }

  toggleStatus(test: TestResponse): void {
    if (this.statusUpdatingId() !== null) {
      return;
    }

    this.statusUpdatingId.set(test.id);
    this.statusError.set(null);
    this.api
      .changeStatus(test.id, { active: !test.active })
      .pipe(
        catchError((error: unknown) => {
          this.statusError.set(this.getStatusErrorMessage(error));
          return EMPTY;
        }),
        finalize(() => this.statusUpdatingId.set(null)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((updatedTest) => {
        this.allTests.update((tests) =>
          tests.map((current) => (current.id === updatedTest.id ? updatedTest : current)),
        );
      });
  }

  get hasSearchQuery(): boolean {
    return this.query().trim().length > 0;
  }

  private loadTests(): void {
    this.loading.set(true);
    this.errorMessage.set(null);
    this.api
      .getAll()
      .pipe(
        catchError((error: unknown) => {
          this.allTests.set([]);
          this.errorMessage.set(this.getErrorMessage(error));
          return EMPTY;
        }),
        finalize(() => this.loading.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((tests) => this.allTests.set(tests));
  }

  private getErrorMessage(error: unknown): string {
    if (error instanceof HttpErrorResponse && error.status === 401) {
      return 'Sesja wygasła lub użytkownik nie jest zalogowany.';
    }
    if (error instanceof HttpErrorResponse && error.status === 403) {
      return 'Brak dostępu do listy testów.';
    }
    return 'Nie udało się pobrać listy testów. Spróbuj ponownie.';
  }

  private getStatusErrorMessage(error: unknown): string {
    if (error instanceof HttpErrorResponse && error.status === 401) {
      return 'Sesja wygasła lub użytkownik nie jest zalogowany.';
    }
    if (error instanceof HttpErrorResponse && error.status === 403) {
      return 'Brak dostępu do zmiany statusu testu.';
    }
    return 'Nie udało się zmienić statusu testu. Spróbuj ponownie.';
  }
}

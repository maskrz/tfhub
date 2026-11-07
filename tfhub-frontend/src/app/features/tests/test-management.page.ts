import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { EMPTY, catchError, finalize } from 'rxjs';
import { TestResponse } from './test.model';
import { TestsApiService } from './tests-api.service';

@Component({
  imports: [RouterLink],
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
}

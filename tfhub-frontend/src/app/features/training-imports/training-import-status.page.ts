import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router } from '@angular/router';
import { EMPTY, Subscription, catchError, exhaustMap, finalize, map, of, take, takeWhile, tap, timer } from 'rxjs';
import { TrainingImportStatus, TrainingImportStatusResponse } from './training-import.model';
import { TrainingImportsApiService } from './training-imports-api.service';

const POLL_INTERVAL_MS = 3000;

@Component({
  selector: 'app-training-import-status-page',
  styleUrl: './training-import-status.page.scss',
  templateUrl: './training-import-status.page.html',
})
export class TrainingImportStatusPage implements OnInit {
  readonly importStatus = signal<TrainingImportStatusResponse | null>(null);
  readonly loading = signal(true);
  readonly polling = signal(false);
  readonly retrying = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly retryErrorMessage = signal<string | null>(null);

  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly importsApi = inject(TrainingImportsApiService);
  private readonly destroyRef = inject(DestroyRef);
  private currentImportId: number | null = null;
  private pollingSubscription: Subscription | null = null;

  ngOnInit(): void {
    const importId = Number(this.route.snapshot.paramMap.get('id'));
    if (!Number.isInteger(importId) || importId <= 0) {
      this.loading.set(false);
      this.errorMessage.set('Nieprawidłowy identyfikator importu.');
      return;
    }
    this.currentImportId = importId;
    this.startPolling(importId);
  }

  retry(): void {
    const importId = this.currentImportId;
    if (importId === null || this.retrying() || this.importStatus()?.status !== 'FAILED') return;

    this.retrying.set(true);
    this.retryErrorMessage.set(null);
    this.importsApi.retry(importId).pipe(
      take(1),
      catchError((error: unknown) => {
        this.retryErrorMessage.set(this.getRequestErrorMessage(error, 'Nie udało się ponowić przetwarzania.'));
        return EMPTY;
      }),
      finalize(() => this.retrying.set(false)),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe((response) => {
      this.importStatus.set(response);
      this.errorMessage.set(null);
      if (this.isActive(response.status)) this.startPolling(importId);
      else this.handleStatus(response);
    });
  }

  statusText(status: TrainingImportStatus): string {
    const labels: Record<TrainingImportStatus, string> = {
      PENDING: 'Oczekuje na przetworzenie',
      PROCESSING: 'Przetwarzanie',
      REVIEW_REQUIRED: 'Wymaga weryfikacji',
      FAILED: 'Nie udało się przetworzyć',
      ACCEPTED: 'Zaakceptowany',
      REJECTED: 'Odrzucony',
    };
    return labels[status];
  }

  private startPolling(importId: number): void {
    this.pollingSubscription?.unsubscribe();
    this.loading.set(true);
    this.polling.set(true);
    this.errorMessage.set(null);
    this.pollingSubscription = timer(0, POLL_INTERVAL_MS).pipe(
      takeWhile(() => this.polling()),
      exhaustMap(() => this.importsApi.getStatus(importId).pipe(
        map((response) => ({ response, error: null as string | null })),
        catchError((error: unknown) => of({
          response: null,
          error: this.getRequestErrorMessage(error, 'Nie udało się pobrać statusu importu. Ponowimy próbę.'),
        })),
      )),
      tap((result) => {
        this.loading.set(false);
        if (result.response) {
          this.importStatus.set(result.response);
          this.errorMessage.set(null);
          if (!this.isActive(result.response.status)) this.polling.set(false);
          this.handleStatus(result.response);
        } else {
          this.errorMessage.set(result.error);
        }
      }),
      takeWhile((result) => result.response === null || this.isActive(result.response.status), true),
      finalize(() => this.polling.set(false)),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe();
  }

  private handleStatus(response: TrainingImportStatusResponse): void {
    if (response.status === 'REVIEW_REQUIRED') {
      void this.router.navigate(['/training-imports', response.id, 'review']);
    }
  }

  private isActive(status: TrainingImportStatus): boolean {
    return status === 'PENDING' || status === 'PROCESSING';
  }

  private getRequestErrorMessage(error: unknown, fallback: string): string {
    if (error instanceof HttpErrorResponse && error.status === 401) {
      return 'Sesja wygasła lub użytkownik nie jest zalogowany.';
    }
    if (error instanceof HttpErrorResponse && error.status === 403) {
      return 'Brak dostępu do tego importu.';
    }
    if (error instanceof HttpErrorResponse && error.status === 404) {
      return 'Nie znaleziono importu treningu.';
    }
    return fallback;
  }
}

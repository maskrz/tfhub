import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { EMPTY, catchError, finalize } from 'rxjs';
import { AthleteResponse, Gender } from './athlete.model';
import { AthletesApiService } from './athletes-api.service';

@Component({
  selector: 'app-athletes-page',
  styleUrl: './athletes.page.scss',
  templateUrl: './athletes.page.html',
})
export class AthletesPage implements OnInit {
  readonly allAthletes = signal<AthleteResponse[]>([]);
  readonly loading = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly query = signal('');
  readonly showInactive = signal(false);
  readonly statusUpdatingId = signal<number | null>(null);
  readonly statusError = signal<string | null>(null);
  readonly visibleAthletes = computed(() => {
    const terms = this.query().trim().toLowerCase().split(/\s+/).filter(Boolean);
    return this.allAthletes().filter((athlete) => {
      if (!this.showInactive() && !athlete.active) {
        return false;
      }

      const firstName = athlete.firstName.toLowerCase();
      const lastName = athlete.lastName.toLowerCase();
      return terms.every((term) => firstName.includes(term) || lastName.includes(term));
    });
  });

  private readonly api = inject(AthletesApiService);
  private readonly destroyRef = inject(DestroyRef);

  ngOnInit(): void {
    this.loadAthletes();
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
    this.loadAthletes();
  }

  genderLabel(gender: Gender): string {
    return gender === 'MALE' ? 'Mężczyzna' : 'Kobieta';
  }

  toggleStatus(athlete: AthleteResponse): void {
    if (this.statusUpdatingId() !== null) {
      return;
    }

    this.statusUpdatingId.set(athlete.id);
    this.statusError.set(null);
    this.api
      .changeStatus(athlete.id, { active: !athlete.active })
      .pipe(
        catchError((error: unknown) => {
          this.statusError.set(this.getStatusErrorMessage(error));
          return EMPTY;
        }),
        finalize(() => this.statusUpdatingId.set(null)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((updatedAthlete) => {
        this.allAthletes.update((athletes) =>
          athletes.map((current) => (current.id === updatedAthlete.id ? updatedAthlete : current)),
        );
      });
  }

  get hasSearchQuery(): boolean {
    return this.query().trim().length > 0;
  }

  private loadAthletes(): void {
    this.loading.set(true);
    this.errorMessage.set(null);
    this.api
      .getAll()
      .pipe(
        catchError((error: unknown) => {
          this.allAthletes.set([]);
          this.errorMessage.set(this.getErrorMessage(error));
          return EMPTY;
        }),
        finalize(() => this.loading.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((athletes) => this.allAthletes.set(athletes));
  }

  private getErrorMessage(error: unknown): string {
    if (error instanceof HttpErrorResponse && error.status === 401) {
      return 'Sesja wygasła lub użytkownik nie jest zalogowany.';
    }
    if (error instanceof HttpErrorResponse && error.status === 403) {
      return 'Brak dostępu do listy zawodników.';
    }
    return 'Nie udało się pobrać listy zawodników. Spróbuj ponownie.';
  }

  private getStatusErrorMessage(error: unknown): string {
    if (error instanceof HttpErrorResponse && error.status === 401) {
      return 'Sesja wygasła lub użytkownik nie jest zalogowany.';
    }
    if (error instanceof HttpErrorResponse && error.status === 403) {
      return 'Brak dostępu do zmiany statusu zawodnika.';
    }
    if (error instanceof HttpErrorResponse && error.status === 404) {
      return 'Nie znaleziono zawodnika do zmiany statusu.';
    }
    return 'Nie udało się zmienić statusu zawodnika. Spróbuj ponownie.';
  }
}

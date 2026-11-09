import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { EMPTY, catchError, finalize } from 'rxjs';
import { RouterLink } from '@angular/router';
import { AthleteFormMode, AthleteResponse, Gender } from './athlete.model';
import { AthletesApiService } from './athletes-api.service';
import { AthleteFormModalComponent } from './athlete-form-modal.component';

@Component({
  imports: [AthleteFormModalComponent, RouterLink],
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
  readonly selectedGender = signal<Gender | ''>('');
  readonly birthYearFrom = signal('');
  readonly birthYearTo = signal('');
  readonly sortColumn = signal<'name' | 'birthYear' | 'gender'>('name');
  readonly sortDirection = signal<'asc' | 'desc'>('asc');
  readonly statusUpdatingId = signal<number | null>(null);
  readonly statusError = signal<string | null>(null);
  readonly isFormOpen = signal(false);
  readonly formMode = signal<AthleteFormMode>('CREATE');
  readonly selectedAthlete = signal<AthleteResponse | null>(null);
  readonly successMessage = signal<string | null>(null);
  readonly visibleAthletes = computed(() => {
    const terms = this.query().trim().toLowerCase().split(/\s+/).filter(Boolean);
    const gender = this.selectedGender();
    const yearFrom = this.parseYear(this.birthYearFrom());
    const yearTo = this.parseYear(this.birthYearTo());
    const direction = this.sortDirection() === 'asc' ? 1 : -1;

    return this.allAthletes().filter((athlete) => {
      if (!this.showInactive() && !athlete.active) return false;
      if (gender && athlete.gender !== gender) return false;
      if (yearFrom !== null && athlete.birthYear < yearFrom) return false;
      if (yearTo !== null && athlete.birthYear > yearTo) return false;
      const firstName = athlete.firstName.toLowerCase();
      const lastName = athlete.lastName.toLowerCase();
      return terms.every(
        (term) =>
          firstName.includes(term) ||
          lastName.includes(term) ||
          this.genderLabel(athlete.gender).toLowerCase().includes(term),
      );
    }).sort((left, right) => {
      if (this.sortColumn() === 'birthYear') {
        return (left.birthYear - right.birthYear) * direction;
      }

      const leftValue = this.sortColumn() === 'name'
        ? left.lastName
        : this.genderLabel(left.gender);
      const rightValue = this.sortColumn() === 'name'
        ? right.lastName
        : this.genderLabel(right.gender);
      const comparison = leftValue.localeCompare(rightValue, 'pl', { sensitivity: 'base' });
      if (comparison !== 0 || this.sortColumn() !== 'name') {
        return comparison * direction;
      }

      return left.firstName.localeCompare(right.firstName, 'pl', { sensitivity: 'base' }) * direction;
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

  onGenderChange(event: Event): void {
    const select = event.target;
    if (select instanceof HTMLSelectElement) {
      const value = select.value;
      if (value === '' || value === 'MALE' || value === 'FEMALE') {
        this.selectedGender.set(value);
      }
    }
  }

  onBirthYearChange(event: Event, bound: 'from' | 'to'): void {
    const input = event.target;
    if (input instanceof HTMLInputElement) {
      if (bound === 'from') {
        this.birthYearFrom.set(input.value);
      } else {
        this.birthYearTo.set(input.value);
      }
    }
  }

  sortBy(column: 'name' | 'birthYear' | 'gender'): void {
    if (this.sortColumn() === column) {
      this.sortDirection.update((direction) => direction === 'asc' ? 'desc' : 'asc');
      return;
    }
    this.sortColumn.set(column);
    this.sortDirection.set('asc');
  }

  openCreateForm(): void {
    this.successMessage.set(null);
    this.formMode.set('CREATE');
    this.selectedAthlete.set(null);
    this.isFormOpen.set(true);
  }

  openEditForm(athlete: AthleteResponse): void {
    this.successMessage.set(null);
    this.formMode.set('EDIT');
    this.selectedAthlete.set(athlete);
    this.isFormOpen.set(true);
  }

  closeForm(): void {
    this.isFormOpen.set(false);
  }

  onAthleteCreated(athlete: AthleteResponse): void {
    this.allAthletes.update((athletes) => [...athletes, athlete]);
    this.isFormOpen.set(false);
    this.successMessage.set('Zawodnik został dodany.');
  }

  onAthleteSaved(athlete: AthleteResponse): void {
    this.allAthletes.update((athletes) =>
      athletes.map((current) => (current.id === athlete.id ? athlete : current)),
    );
    this.isFormOpen.set(false);
    this.successMessage.set('Zmiany zawodnika zostały zapisane.');
  }

  onFormSaved(athlete: AthleteResponse): void {
    if (this.formMode() === 'CREATE') {
      this.onAthleteCreated(athlete);
      return;
    }
    this.onAthleteSaved(athlete);
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
    return (
      this.query().trim().length > 0 ||
      this.selectedGender() !== '' ||
      this.birthYearFrom().trim().length > 0 ||
      this.birthYearTo().trim().length > 0
    );
  }

  private parseYear(value: string): number | null {
    if (!value.trim()) return null;
    const year = Number(value);
    return Number.isInteger(year) ? year : null;
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

import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { EMPTY, catchError, finalize, take } from 'rxjs';
import { CompetitionApiService } from './competition-api.service';
import { CompetitionEvent } from './competition.model';
import { CompetitionEventFormComponent } from './competition-event-form.component';
import { ModalDialogDirective } from '../../shared/modal-dialog.directive';

@Component({
  imports: [CompetitionEventFormComponent, ModalDialogDirective],
  selector: 'app-competition-events-page',
  templateUrl: './competition-events.page.html',
  styleUrl: './competition-events.page.scss',
})
export class CompetitionEventsPage implements OnInit {
  readonly events = signal<CompetitionEvent[]>([]);
  readonly loading = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly statusError = signal<string | null>(null);
  readonly formOpen = signal(false);
  readonly selectedEvent = signal<CompetitionEvent | null>(null);
  readonly updatingId = signal<number | null>(null);
  readonly query = signal('');
  readonly showInactive = signal(false);
  readonly sortDirection = signal<'asc' | 'desc'>('asc');
  readonly visibleEvents = computed(() => {
    const query = this.query().trim().toLocaleLowerCase('pl');
    const direction = this.sortDirection() === 'asc' ? 1 : -1;
    return this.events()
      .filter((event) => (this.showInactive() || event.active)
        && (!query || event.name.toLocaleLowerCase('pl').includes(query)))
      .sort((left, right) => left.name.localeCompare(right.name, 'pl', { sensitivity: 'base', numeric: true }) * direction);
  });

  private readonly api = inject(CompetitionApiService);
  private readonly destroyRef = inject(DestroyRef);

  ngOnInit(): void {
    this.load();
  }

  onSearchInput(event: Event): void {
    if (event.target instanceof HTMLInputElement) this.query.set(event.target.value);
  }

  onInactiveToggle(event: Event): void {
    if (event.target instanceof HTMLInputElement) this.showInactive.set(event.target.checked);
  }

  sortByName(): void {
    this.sortDirection.update((direction) => direction === 'asc' ? 'desc' : 'asc');
  }

  openCreate(): void {
    this.selectedEvent.set(null);
    this.formOpen.set(true);
  }

  openEdit(event: CompetitionEvent): void {
    this.selectedEvent.set(event);
    this.formOpen.set(true);
  }

  onSaved(event: CompetitionEvent): void {
    this.formOpen.set(false);
    this.events.update((events) => {
      const index = events.findIndex((item) => item.id === event.id);
      if (index < 0) return [...events, event].sort((a, b) => a.name.localeCompare(b.name, 'pl'));
      return events.map((item) => item.id === event.id ? event : item);
    });
  }

  toggleStatus(event: CompetitionEvent): void {
    if (this.updatingId() !== null) return;
    this.updatingId.set(event.id);
    this.statusError.set(null);
    this.api.changeEventStatus(event.id, { active: !event.active }).pipe(
      take(1),
      catchError((error: unknown) => {
        this.statusError.set('Nie udało się zmienić statusu konkurencji.');
        return EMPTY;
      }),
      finalize(() => this.updatingId.set(null)),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe((updated) => {
      this.events.update((events) => events.map((item) => item.id === updated.id ? updated : item));
    });
  }

  retry(): void {
    this.load();
  }

  private load(): void {
    this.loading.set(true);
    this.errorMessage.set(null);
    this.api.getEvents().pipe(
      take(1),
      catchError(() => {
        this.errorMessage.set('Nie udało się pobrać konkurencji.');
        return EMPTY;
      }),
      finalize(() => this.loading.set(false)),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe((events) => this.events.set(events));
  }
}

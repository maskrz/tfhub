import { HttpErrorResponse } from '@angular/common/http';
import { Component, EventEmitter, Input, OnInit, Output, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { EMPTY, catchError, finalize, take } from 'rxjs';
import { CompetitionApiService } from './competition-api.service';
import {
  CompetitionEvent,
  CompetitionResult,
  CompetitionSeasonType,
  SaveCompetitionResultRequest,
} from './competition.model';

@Component({
  imports: [ReactiveFormsModule],
  selector: 'app-competition-result-form',
  template: `
    <form class="result-form" [formGroup]="form" (ngSubmit)="submit()" novalidate>
      <h2 id="competition-result-form-title">{{ result ? 'Edytuj wynik zawodów' : 'Dodaj wynik zawodów' }}</h2>
      @if (loadingEvents()) {
        <p role="status">Ładowanie konkurencji...</p>
      }       @else if (eventsError(); as message) {
        <p class="feedback error" role="alert">{{ message }}</p>
      } @else {
        <label class="field">
          <span>Konkurencja</span>
          <select class="field-control select-with-arrow" formControlName="eventId">
            <option [ngValue]="null" disabled>Wybierz konkurencję</option>
            @for (event of selectableEvents(); track event.id) {
              <option [ngValue]="event.id">{{ event.name }} ({{ event.unit }}){{ event.active ? '' : ' — nieaktywna' }}</option>
            }
          </select>
        </label>
        <div class="field-grid">
          <label class="field"><span>Data wyniku</span><input class="field-control" type="date" formControlName="resultDate" /></label>
          <label class="field"><span>Wynik</span><input class="field-control" type="text" maxlength="50" formControlName="originalMark" /></label>
          <label class="field"><span>Wartość liczbowa (opcjonalnie)</span><input class="field-control" type="number" step="any" formControlName="numericValue" /></label>
          <label class="field"><span>Wiatr (m/s, opcjonalnie)</span><input class="field-control" type="number" step="0.01" formControlName="wind" /></label>
          <label class="field"><span>Sezon (rok)</span><input class="field-control" type="number" min="1900" max="9999" formControlName="season" /></label>
          <label class="field">
            <span>Sezon</span>
            <select class="field-control select-with-arrow" formControlName="seasonType">
              <option value="OUTDOOR">Na stadionie otwartym</option>
              <option value="INDOOR">W hali</option>
              <option value="UNKNOWN">Aktualny</option>
            </select>
          </label>
        </div>
        <label class="field"><span>Zawody</span><input class="field-control" type="text" maxlength="250" formControlName="competitionName" /></label>
        <label class="field"><span>Miejscowość</span><input class="field-control" type="text" maxlength="250" formControlName="location" /></label>
        <label class="field"><span>Runda</span><input class="field-control" type="text" maxlength="100" formControlName="resultRound" /></label>
      }
      @if (errorMessage(); as message) {
        <p class="feedback error" role="alert">{{ message }}</p>
      }
      <div class="form-actions">
        <button type="button" class="button button--secondary" [disabled]="saving()" (click)="cancelled.emit()">Anuluj</button>
        <button type="submit" class="button button--primary" [disabled]="saving() || loadingEvents() || !!eventsError()">
          {{ saving() ? 'Zapisywanie...' : 'Zapisz' }}
        </button>
      </div>
    </form>
  `,
  styles: `
    .result-form { display: grid; gap: .85rem; max-height: min(85vh, 52rem); overflow: auto; }
    h2 { margin: 0; }
    .field { display: grid; gap: .3rem; min-width: 0; }
    .field-grid { display: grid; gap: .7rem; grid-template-columns: repeat(2, minmax(0, 1fr)); }
    .form-actions { display: flex; gap: .6rem; justify-content: flex-end; position: sticky; bottom: 0; background: #fff; padding-top: .5rem; }
    @media (max-width: 38rem) { .field-grid { grid-template-columns: 1fr; } }
  `,
})
export class CompetitionResultFormComponent implements OnInit {
  @Input({ required: true }) athleteId!: number;
  @Input() result: CompetitionResult | null = null;
  @Output() saved = new EventEmitter<void>();
  @Output() cancelled = new EventEmitter<void>();

  readonly events = signal<CompetitionEvent[]>([]);
  readonly loadingEvents = signal(false);
  readonly eventsError = signal<string | null>(null);
  readonly saving = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly form = new FormGroup({
    eventId: new FormControl<number | null>(null, Validators.required),
    resultDate: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    originalMark: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(50)] }),
    numericValue: new FormControl<number | null>(null),
    wind: new FormControl<number | null>(null),
    season: new FormControl<number | null>(null),
    seasonType: new FormControl<CompetitionSeasonType>('UNKNOWN', { nonNullable: true, validators: [Validators.required] }),
    competitionName: new FormControl('', { nonNullable: true, validators: [Validators.maxLength(250)] }),
    location: new FormControl('', { nonNullable: true, validators: [Validators.maxLength(250)] }),
    resultRound: new FormControl('', { nonNullable: true, validators: [Validators.maxLength(100)] }),
  });

  private readonly api = inject(CompetitionApiService);

  selectableEvents(): CompetitionEvent[] {
    return this.events().filter((event) => event.active || event.id === this.result?.eventId);
  }

  ngOnInit(): void {
    if (this.result) {
      this.form.setValue({
        eventId: this.result.eventId,
        resultDate: this.result.resultDate,
        originalMark: this.result.originalMark,
        numericValue: this.result.numericValue,
        wind: this.result.wind,
        season: this.result.season,
        seasonType: this.result.seasonType,
        competitionName: this.result.competitionName ?? '',
        location: this.result.location ?? '',
        resultRound: this.result.resultRound ?? '',
      });
    }
    this.loadingEvents.set(true);
    this.api.getEvents().pipe(
      take(1),
      catchError((error: unknown) => {
        this.eventsError.set(error instanceof HttpErrorResponse && error.status === 403
          ? 'Brak dostępu do zarządzania konkurencjami.'
          : 'Nie udało się pobrać konkurencji.');
        return EMPTY;
      }),
      finalize(() => this.loadingEvents.set(false)),
    ).subscribe((events) => this.events.set(events));
  }

  submit(): void {
    if (this.saving() || this.loadingEvents() || this.eventsError()) return;
    this.errorMessage.set(null);
    this.form.markAllAsTouched();
    if (this.form.invalid) {
      this.errorMessage.set('Uzupełnij konkurencję, datę i wynik.');
      return;
    }
    const value = this.form.getRawValue();
    if (value.eventId === null) return;
    this.saving.set(true);
    this.errorMessage.set(null);
    const request: SaveCompetitionResultRequest = {
      eventId: value.eventId,
      resultDate: value.resultDate,
      originalMark: value.originalMark.trim(),
      numericValue: value.numericValue,
      wind: value.wind,
      season: value.season,
      seasonType: value.seasonType,
      competitionName: value.competitionName.trim() || null,
      location: value.location.trim() || null,
      resultRound: value.resultRound.trim() || null,
    };
    const save = this.result
      ? this.api.updateResult(this.result.id, request)
      : this.api.createResult(this.athleteId, request);
    save.pipe(
      take(1),
      catchError(() => {
        this.errorMessage.set('Nie udało się zapisać wyniku zawodów. Sprawdź dane i spróbuj ponownie.');
        return EMPTY;
      }),
      finalize(() => this.saving.set(false)),
    ).subscribe(() => this.saved.emit());
  }
}

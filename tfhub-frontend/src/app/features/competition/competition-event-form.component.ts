import { HttpErrorResponse } from '@angular/common/http';
import { Component, EventEmitter, Input, Output, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { EMPTY, catchError, finalize, take } from 'rxjs';
import { CompetitionApiService } from './competition-api.service';
import { CompetitionEvent } from './competition.model';

@Component({
  imports: [ReactiveFormsModule],
  selector: 'app-competition-event-form',
  template: `
    <form class="event-form" [formGroup]="form" (ngSubmit)="submit()" novalidate>
      <h2 id="event-form-title">{{ event ? 'Edytuj konkurencję' : 'Dodaj konkurencję' }}</h2>
      @if (errorMessage(); as message) {
        <p class="feedback error" role="alert">{{ message }}</p>
      }
      <label class="field">
        <span>Nazwa konkurencji</span>
        <input class="field-control" type="text" formControlName="name" maxlength="150" />
      </label>
      <label class="field">
        <span>Jednostka</span>
        <input class="field-control" type="text" formControlName="unit" maxlength="20" />
      </label>
      <div class="form-actions">
        <button type="button" class="button button--secondary" [disabled]="saving()" (click)="cancelled.emit()">Anuluj</button>
        <button type="submit" class="button button--primary" [disabled]="saving()">
          {{ saving() ? 'Zapisywanie...' : 'Zapisz' }}
        </button>
      </div>
    </form>
  `,
  styles: `
    .event-form { display: grid; gap: 1rem; }
    h2 { margin: 0; }
    .field { display: grid; gap: .35rem; }
    .form-actions { display: flex; gap: .6rem; justify-content: flex-end; }
  `,
})
export class CompetitionEventFormComponent {
  @Input() event: CompetitionEvent | null = null;
  @Output() saved = new EventEmitter<CompetitionEvent>();
  @Output() cancelled = new EventEmitter<void>();

  readonly saving = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly form = new FormGroup({
    name: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(150)] }),
    unit: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(20)] }),
  });

  private readonly api = inject(CompetitionApiService);

  ngOnInit(): void {
    if (this.event) this.form.setValue({ name: this.event.name, unit: this.event.unit });
  }

  submit(): void {
    if (this.saving()) return;
    this.form.markAllAsTouched();
    if (this.form.invalid) {
      this.errorMessage.set('Podaj nazwę konkurencji i jednostkę, pamiętając o limitach długości.');
      return;
    }
    this.saving.set(true);
    this.errorMessage.set(null);
    const request = {
      name: this.form.controls.name.value.trim(),
      unit: this.form.controls.unit.value.trim(),
    };
    const save = this.event
      ? this.api.updateEvent(this.event.id, request)
      : this.api.createEvent(request);
    save.pipe(
      take(1),
      catchError((error: unknown) => {
        const title = error instanceof HttpErrorResponse
          ? (error.error as { title?: string } | null)?.title
          : null;
        this.errorMessage.set(title === 'Competition event already exists'
          ? 'Konkurencja o tej nazwie już istnieje.'
          : title === 'Competition event configuration cannot be changed'
            ? 'Nie można zmienić jednostki konkurencji, do której przypisano wyniki.'
            : 'Nie udało się zapisać konkurencji.');
        return EMPTY;
      }),
      finalize(() => this.saving.set(false)),
    ).subscribe((event) => this.saved.emit(event));
  }
}

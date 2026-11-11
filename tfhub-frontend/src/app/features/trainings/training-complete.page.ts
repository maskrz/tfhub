import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { AbstractControl, FormArray, FormControl, FormGroup, ReactiveFormsModule, ValidatorFn } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { EMPTY, catchError, finalize, take } from 'rxjs';
import { MultiSelectDropdownComponent, MultiSelectOption } from '../../shared/multi-select-dropdown.component';
import { AthleteResponse } from '../athletes/athlete.model';
import { AthletesApiService } from '../athletes/athletes-api.service';
import {
  CompleteTrainingRequest,
  TrainingDetails,
  TrainingExecutionStatus,
  TrainingParticipationDetails,
} from './training.model';
import { TrainingsApiService } from './trainings-api.service';

type ExecutionForm = FormGroup<{
  id: FormControl<number | null>;
  segmentId: FormControl<number>;
  status: FormControl<TrainingExecutionStatus>;
  measuredResult: FormControl<string>;
  usesSpikes: FormControl<boolean>;
}>;

type ParticipationForm = FormGroup<{
  id: FormControl<number | null>;
  athleteId: FormControl<number>;
  firstName: FormControl<string>;
  lastName: FormControl<string>;
  executions: FormArray<ExecutionForm>;
}>;

type DefinitionForm = FormGroup<{
  id: FormControl<number>;
  participations: FormArray<ParticipationForm>;
}>;

const executionValidator: ValidatorFn = (control) => {
  const form = control as ExecutionForm;
  const value = form.controls.measuredResult.value.trim();
  const status = form.controls.status.value;
  if (status === 'PENDING' && value) return { pendingValue: true };
  if (status === 'RECORDED' && !value) return { resultRequired: true };
  if (status === 'RECORDED' && parseTimeSeconds(value) === null) return { resultFormat: true };
  if (status === 'COMPLETED_WITHOUT_RESULT' && value.toUpperCase() !== 'X') return { statusValue: true };
  if (status === 'NOT_COMPLETED' && value !== '-') return { statusValue: true };
  return null;
};

function createExecution(execution: {
  id: number | null;
  segmentId: number;
  status: TrainingExecutionStatus;
  measuredResult: number | null;
  usesSpikes: boolean;
}): ExecutionForm {
  const displayValue = execution.measuredResult !== null
    ? formatResult(execution.measuredResult)
    : execution.status === 'COMPLETED_WITHOUT_RESULT'
      ? 'X'
      : execution.status === 'NOT_COMPLETED'
        ? '-'
        : '';
  return new FormGroup({
    id: new FormControl(execution.id),
    segmentId: new FormControl(execution.segmentId, { nonNullable: true }),
    status: new FormControl(execution.status, { nonNullable: true }),
    measuredResult: new FormControl(displayValue, { nonNullable: true }),
    usesSpikes: new FormControl(execution.usesSpikes, { nonNullable: true }),
  }, { validators: [executionValidator] });
}

function formatResult(seconds: number): string {
  const hundredths = Math.round(seconds * 100);
  const minutes = Math.floor(hundredths / 6000);
  const secondsWithinMinute = Math.floor((hundredths % 6000) / 100);
  const fraction = hundredths % 100;
  const secondsText = `${String(secondsWithinMinute).padStart(2, '0')},${String(fraction).padStart(2, '0')}`;
  return minutes > 0 ? `${minutes}:${secondsText}` : secondsText;
}

function normalizeTimeInput(input: string): string | null {
  let value = input.trim();
  if (!value) return '';
  if (value.toUpperCase() === 'X') return 'X';
  if (value === '-') return '-';

  value = value.replace(/;/g, ':');
  const separators = [...value.matchAll(/[,.]/g)];
  let singleDotFraction = false;
  if (separators.length >= 2) {
    const first = separators[0].index!;
    const second = separators[1].index!;
    value = `${value.slice(0, first)}:${value.slice(first + 1, second)},${value.slice(second + 1)}`;
  } else if (separators.length === 1) {
    const index = separators[0].index!;
    singleDotFraction = value[index] === '.';
    value = `${value.slice(0, index)},${value.slice(index + 1)}`;
  }

  const match = /^(?:(\d+):)?(\d+)(?:,(\d{1,2}))?$/.exec(value);
  if (!match) return null;
  const [, minutes, seconds, fraction] = match;
  if (minutes !== undefined && Number(seconds) > 59) return null;
  const secondsText = minutes !== undefined ? seconds.padStart(2, '0') : seconds;
  const fractionText = fraction && fraction.length === 1 && !singleDotFraction
    ? `${fraction}0`
    : fraction;
  return `${minutes === undefined ? '' : `${minutes}:`}${secondsText}${fractionText ? `,${fractionText}` : ''}`;
}

function parseTimeSeconds(input: string): number | null {
  const normalized = normalizeTimeInput(input);
  if (!normalized || normalized === 'X' || normalized === '-') return null;
  const match = /^(?:(\d+):)?(\d+)(?:,(\d{1,2}))?$/.exec(normalized);
  if (!match) return null;
  const [, minutes, seconds, fraction] = match;
  return Number(minutes ?? 0) * 60 + Number(seconds) + Number(`0.${fraction ?? '0'}`);
}

@Component({
  imports: [MultiSelectDropdownComponent, ReactiveFormsModule, RouterLink],
  selector: 'app-training-complete-page',
  styleUrl: './training-complete.page.scss',
  templateUrl: './training-complete.page.html',
})
export class TrainingCompletePage implements OnInit, OnDestroy {
  readonly training = signal<TrainingDetails | null>(null);
  readonly athletes = signal<AthleteResponse[]>([]);
  readonly loading = signal(true);
  readonly athletesLoading = signal(false);
  readonly submitting = signal(false);
  readonly attemptedSubmit = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly loadError = signal<string | null>(null);
  readonly assignmentMessage = signal<string | null>(null);
  readonly successMessage = signal<string | null>(null);
  readonly form = new FormGroup({
    definitions: new FormArray<DefinitionForm>([]),
  });

  private readonly route = inject(ActivatedRoute);
  private readonly trainingsApi = inject(TrainingsApiService);
  private readonly athletesApi = inject(AthletesApiService);
  private readonly destroyRef = inject(DestroyRef);
  private trainingId = 0;
  private athleteLoadId = 0;
  private successTimeout: ReturnType<typeof setTimeout> | null = null;

  get definitions(): FormArray<DefinitionForm> {
    return this.form.controls.definitions;
  }

  ngOnInit(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    if (!Number.isSafeInteger(id) || id <= 0) {
      this.loading.set(false);
      this.loadError.set('Nieprawidłowy identyfikator treningu.');
      return;
    }
    this.trainingId = id;
    this.loadTraining(id);
  }

  ngOnDestroy(): void {
    this.clearSuccessTimeout();
  }

  participants(definitionIndex: number): FormArray<ParticipationForm> {
    return this.definitions.at(definitionIndex).controls.participations;
  }

  optionsFor(definitionIndex: number): MultiSelectOption[] {
    const training = this.training();
    if (!training) return [];
    const assignedElsewhere = new Set(this.definitions.controls
      .filter((_, index) => index !== definitionIndex)
      .flatMap((definition) => definition.controls.participations.controls
        .map((participant) => participant.controls.athleteId.value)));
    const options = new Map<number, MultiSelectOption>();
    for (const athlete of this.athletes()) {
      if (athlete.trainingGroupId === training.trainingGroup.id) {
        options.set(athlete.id, { id: athlete.id, name: `${athlete.firstName} ${athlete.lastName}` });
      }
    }
    for (const participant of this.participants(definitionIndex).controls) {
      options.set(participant.controls.athleteId.value, {
        id: participant.controls.athleteId.value,
        name: `${participant.controls.firstName.value} ${participant.controls.lastName.value}`,
      });
    }
    return [...options.values()].filter((option) => !assignedElsewhere.has(option.id));
  }

  selectedIds(definitionIndex: number): number[] {
    return this.participants(definitionIndex).controls.map((value) => value.controls.athleteId.value);
  }

  onAthletesChange(definitionIndex: number, selectedIds: number[]): void {
    this.assignmentMessage.set(null);
    const participants = this.participants(definitionIndex);
    const options = new Map(this.optionsFor(definitionIndex).map((option) => [option.id, option]));
    const selected = [...new Set(selectedIds)].filter((id) => options.has(id));
    const retained = new Map(participants.controls.map((participant) => [
      participant.controls.athleteId.value,
      participant,
    ]));
    const selectedSet = new Set(selected);
    const recordedRemoval = participants.controls.some((participant) => {
      if (selectedSet.has(participant.controls.athleteId.value)) return false;
      const participationId = participant.controls.id.value;
      if (participationId === null) return false;
      return this.training()?.definitions.flatMap((definition) => definition.participations)
        .find((original) => original.id === participationId)
        ?.executions.some((execution) => execution.status === 'RECORDED') ?? false;
    });
    if (recordedRemoval) {
      this.assignmentMessage.set('Nie można usunąć ani przenieść zawodnika z zapisanym wynikiem.');
      return;
    }
    const next = selected.map((athleteId) =>
      retained.get(athleteId) ?? this.createParticipation(athleteId, definitionIndex));
    participants.clear();
    next.forEach((participant) => participants.push(participant));
  }

  resultChanged(execution: ExecutionForm): void {
    const result = execution.controls.measuredResult;
    const value = result.value.trim().toUpperCase();
    if (value === 'X') {
      execution.controls.status.setValue('COMPLETED_WITHOUT_RESULT');
    } else if (value === '-') {
      execution.controls.status.setValue('NOT_COMPLETED');
    } else if (!value) {
      execution.controls.status.setValue('PENDING');
    } else {
      execution.controls.status.setValue('RECORDED');
    }
    execution.updateValueAndValidity();
  }

  formatResultOnBlur(execution: ExecutionForm): void {
    const control = execution.controls.measuredResult;
    const normalized = normalizeTimeInput(control.value);
    if (normalized !== null) control.setValue(normalized);
    this.resultChanged(execution);
    execution.updateValueAndValidity();
  }

  resultInvalid(execution: ExecutionForm): boolean {
    return execution.invalid && (execution.touched || this.attemptedSubmit());
  }

  resultInputId(definitionIndex: number, athleteId: number, segmentId: number): string {
    return `execution-result-${definitionIndex}-${athleteId}-${segmentId}`;
  }

  resultAriaLabel(participant: ParticipationForm, segmentSequenceNumber: number): string {
    return `Wynik: ${participant.controls.firstName.value} ${participant.controls.lastName.value}, odcinek ${segmentSequenceNumber}`;
  }

  spikesAriaLabel(participant: ParticipationForm, segmentSequenceNumber: number): string {
    return `Kolce: ${participant.controls.firstName.value} ${participant.controls.lastName.value}, odcinek ${segmentSequenceNumber}`;
  }

  segmentPlanLabel(distanceM: number, restAfterSeconds: number | null): string {
    const distance = `${distanceM}m`;
    if (restAfterSeconds === null) return distance;
    const minutes = Math.floor(restAfterSeconds / 60);
    const seconds = restAfterSeconds % 60;
    const rest = seconds === 0
      ? `${minutes}min`
      : minutes === 0
        ? `${seconds}s`
        : `${minutes}min ${seconds}s`;
    return `${distance} / ${rest}`;
  }

  submit(): void {
    if (this.submitting() || !this.training()) return;
    this.attemptedSubmit.set(true);
    this.errorMessage.set(null);
    this.dismissSuccessMessage();
    this.form.markAllAsTouched();
    if (this.form.invalid) return;
    this.submitting.set(true);
    this.trainingsApi.complete(this.trainingId, this.toRequest()).pipe(
      take(1),
      catchError((error: unknown) => {
        this.errorMessage.set(this.getErrorMessage(error));
        return EMPTY;
      }),
      finalize(() => this.submitting.set(false)),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe(() => {
      this.showSuccessMessage('Uzupełnienie treningu zostało zapisane.');
      this.refreshAfterSave();
    });
  }

  dismissSuccessMessage(): void {
    this.clearSuccessTimeout();
    this.successMessage.set(null);
  }

  retry(): void {
    if (this.trainingId > 0) this.loadTraining(this.trainingId);
  }

  executionFor(participant: ParticipationForm, segmentId: number): ExecutionForm | undefined {
    return participant.controls.executions.controls.find(
      (execution) => execution.controls.segmentId.value === segmentId,
    );
  }

  private loadTraining(id: number): void {
    this.loading.set(true);
    this.loadError.set(null);
    this.trainingsApi.getById(id).pipe(
      take(1),
      catchError((error: unknown) => {
        this.loadError.set(this.getLoadError(error));
        return EMPTY;
      }),
      finalize(() => this.loading.set(false)),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe((training) => {
      if (!training.active) {
        this.loadError.set('Nie można uzupełnić nieaktywnego treningu.');
        return;
      }
      this.training.set(training);
      this.populate(training);
      this.loadAthletes(training.trainingGroup.id);
    });
  }

  private refreshAfterSave(): void {
    this.trainingsApi.getById(this.trainingId).pipe(
      take(1),
      catchError(() => {
        this.errorMessage.set('Zapisano zmiany, ale nie udało się odświeżyć danych. Odśwież stronę przed kolejnym zapisem.');
        return EMPTY;
      }),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe((training) => {
      this.training.set(training);
      this.populate(training);
    });
  }

  private showSuccessMessage(message: string): void {
    this.clearSuccessTimeout();
    this.successMessage.set(message);
    this.successTimeout = setTimeout(() => {
      this.successMessage.set(null);
      this.successTimeout = null;
    }, 5000);
  }

  private clearSuccessTimeout(): void {
    if (this.successTimeout !== null) {
      clearTimeout(this.successTimeout);
      this.successTimeout = null;
    }
  }

  private populate(training: TrainingDetails): void {
    this.definitions.clear();
    for (const definition of training.definitions) {
      const participants = definition.participations.map((participant) =>
        this.fromDetails(participant));
      this.definitions.push(new FormGroup({
        id: new FormControl(definition.id, { nonNullable: true }),
        participations: new FormArray(participants),
      }));
    }
  }

  private fromDetails(participant: TrainingParticipationDetails): ParticipationForm {
    return new FormGroup({
      id: new FormControl(participant.id),
      athleteId: new FormControl(participant.athleteId, { nonNullable: true }),
      firstName: new FormControl(participant.firstName, { nonNullable: true }),
      lastName: new FormControl(participant.lastName, { nonNullable: true }),
      executions: new FormArray(participant.executions.map((execution) => createExecution({
        id: execution.id,
        segmentId: execution.segmentId,
        status: execution.status,
        measuredResult: execution.measuredResult,
        usesSpikes: execution.usesSpikes,
      }))),
    });
  }

  private createParticipation(athleteId: number, definitionIndex: number): ParticipationForm {
    const athlete = this.athletes().find((item) => item.id === athleteId);
    const segments = this.training()?.definitions[definitionIndex]?.segments ?? [];
    const executions = segments.map((segment) => createExecution({
      id: null,
      segmentId: segment.id,
      status: 'PENDING',
      measuredResult: null,
      usesSpikes: false,
    }));
    return new FormGroup({
      id: new FormControl<number | null>(null),
      athleteId: new FormControl(athleteId, { nonNullable: true }),
      firstName: new FormControl(athlete?.firstName ?? '', { nonNullable: true }),
      lastName: new FormControl(athlete?.lastName ?? '', { nonNullable: true }),
      executions: new FormArray(executions),
    });
  }

  private loadAthletes(groupId: number): void {
    const loadId = ++this.athleteLoadId;
    this.athletesLoading.set(true);
    this.athletesApi.getAll().pipe(
      take(1),
      catchError((error: unknown) => {
        if (loadId === this.athleteLoadId) {
          this.errorMessage.set(this.getLoadError(error));
        }
        return EMPTY;
      }),
      finalize(() => {
        if (loadId === this.athleteLoadId) this.athletesLoading.set(false);
      }),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe((athletes) => {
      if (loadId === this.athleteLoadId && this.training()?.trainingGroup.id === groupId) {
        this.athletes.set(athletes);
      }
    });
  }

  private toRequest(): CompleteTrainingRequest {
    const training = this.training()!;
    const current = this.definitions.controls.flatMap((definition) =>
      definition.controls.participations.controls.map((participant) => ({
        definitionId: definition.controls.id.value,
        participant,
      })));
    const currentIds = new Set(current.map(({ participant }) => participant.controls.id.value)
      .filter((id): id is number => id !== null));
    const original = training.definitions.flatMap((definition) => definition.participations);
    const originalExecutions = new Map(original.flatMap((participant) => participant.executions)
      .map((execution) => [execution.id, execution]));
    const add = current.filter(({ participant }) => participant.controls.id.value === null)
      .map(({ definitionId, participant }) => ({
        definitionId,
        athleteId: participant.controls.athleteId.value,
        executionUpdates: participant.controls.executions.controls
          .filter((execution) =>
            execution.controls.status.value !== 'PENDING' ||
            execution.controls.measuredResult.value.trim() !== '' ||
            execution.controls.usesSpikes.value)
          .map((execution) => ({
            segmentId: execution.controls.segmentId.value,
            status: execution.controls.status.value,
            measuredResult: this.parseResult(execution.controls.measuredResult.value),
            usesSpikes: execution.controls.usesSpikes.value,
          })),
      }));
    const update = current.filter(({ participant }) => participant.controls.id.value !== null)
      .map(({ participant }) => ({
        id: participant.controls.id.value!,
        executionUpdates: participant.controls.executions.controls
          .filter((execution) => {
            const originalExecution = originalExecutions.get(execution.controls.id.value!);
            return originalExecution !== undefined &&
              (originalExecution.status !== execution.controls.status.value ||
                originalExecution.measuredResult !== this.parseResult(execution.controls.measuredResult.value) ||
                originalExecution.usesSpikes !== execution.controls.usesSpikes.value);
          })
          .map((execution) => ({
            id: execution.controls.id.value!,
            status: execution.controls.status.value,
            measuredResult: this.parseResult(execution.controls.measuredResult.value),
            usesSpikes: execution.controls.usesSpikes.value,
          })),
      }))
      .filter((participant) => participant.executionUpdates.length > 0);
    return {
      add,
      update,
      removeParticipationIds: original
        .filter((participant) => !currentIds.has(participant.id))
        .map((participant) => participant.id),
    };
  }

  private parseResult(value: string): number | null {
    return parseTimeSeconds(value);
  }

  private getLoadError(error: unknown): string {
    if (error instanceof HttpErrorResponse && error.status === 401) return 'Sesja wygasła lub użytkownik nie jest zalogowany.';
    if (error instanceof HttpErrorResponse && error.status === 403) return 'Brak dostępu do tego treningu.';
    if (error instanceof HttpErrorResponse && error.status === 404) return 'Nie znaleziono treningu.';
    return 'Nie udało się pobrać danych treningu. Spróbuj ponownie.';
  }

  private getErrorMessage(error: unknown): string {
    if (!(error instanceof HttpErrorResponse)) return 'Wystąpił nieoczekiwany błąd. Wprowadzone zmiany nie zostały utracone.';
    if (error.status === 401) return 'Sesja wygasła lub użytkownik nie jest zalogowany.';
    if (error.status === 403) return 'Brak dostępu do uzupełnienia tego treningu.';
    if (error.status === 404) return 'Nie znaleziono treningu.';
    const detail = (error.error as { detail?: string } | null)?.detail;
    if (detail?.includes('currently belong to the training group')) return 'Nowo przypisany zawodnik musi należeć do grupy treningu.';
    if (detail?.includes('recorded execution data')) return 'Nie można usunąć ani przenieść zawodnika z zapisanym wynikiem.';
    if (detail?.includes('only one definition')) return 'Zawodnik może być przypisany tylko do jednej podgrupy.';
    return 'Nie udało się zapisać zmian. Wprowadzone dane nie zostały utracone.';
  }
}

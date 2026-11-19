import { HttpErrorResponse } from '@angular/common/http';
import { AfterViewInit, Component, DestroyRef, ElementRef, EventEmitter, Output, ViewChild, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { EMPTY, catchError, finalize, take } from 'rxjs';
import { AthleteResponse } from '../athletes/athlete.model';
import { AthletesApiService } from '../athletes/athletes-api.service';
import { MultiSelectDropdownComponent, MultiSelectOption } from '../../shared/multi-select-dropdown.component';
import { TrainingGroup, TrainingGroupsApiService } from '../training-groups/training-groups-api.service';
import {
  FreeTextTrainingTransfer,
  TrainingAssignmentType,
  TrainingJsonDocument,
  TrainingJsonImportMetadata,
  TrainingJsonImportResponse,
  TrainingTransfer,
} from './training.model';
import { TrainingsApiService } from './trainings-api.service';

interface TrainingImportRow {
  training: TrainingTransfer;
  selected: boolean;
  trainingDate: string;
  trainingGroupId: number | null;
  assignmentType: TrainingAssignmentType;
  athleteIds: number[];
}

const MAX_FILE_BYTES = 1024 * 1024;
const FILE_EXTENSION = /\.json$/i;

@Component({
  imports: [MultiSelectDropdownComponent],
  selector: 'app-training-json-import-dialog',
  styleUrl: './training-json-import-dialog.component.scss',
  templateUrl: './training-json-import-dialog.component.html',
})
export class TrainingJsonImportDialogComponent implements AfterViewInit {
  @Output() imported = new EventEmitter<TrainingJsonImportResponse>();
  @Output() cancelled = new EventEmitter<void>();
  @ViewChild('dialog') private dialog!: ElementRef<HTMLDialogElement>;

  readonly file = signal<File | null>(null);
  readonly document = signal<TrainingJsonDocument | null>(null);
  readonly rows = signal<TrainingImportRow[]>([]);
  readonly validating = signal(false);
  readonly importing = signal(false);
  readonly attemptedSubmit = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly fileError = signal<string | null>(null);
  readonly groups = signal<TrainingGroup[]>([]);
  readonly groupsLoading = signal(false);
  readonly groupsError = signal<string | null>(null);
  readonly athletes = signal<AthleteResponse[]>([]);
  readonly athletesLoading = signal(false);
  readonly athletesError = signal<string | null>(null);
  readonly commonGroupId = signal<number | null>(null);
  readonly commonTrainingDate = signal('');
  readonly athleteOptions = computed<MultiSelectOption[]>(() => this.athletes().map((athlete) => ({
    id: athlete.id,
    name: `${athlete.firstName} ${athlete.lastName}`,
  })));
  readonly selectedCount = computed(() => this.rows().filter((row) => row.selected).length);
  readonly canImport = computed(() => {
    const rows = this.rows();
    const selectedRows = rows.filter((row) => row.selected);
    return this.document() !== null
      && !this.importing()
      && !this.validating()
      && selectedRows.length > 0
      && selectedRows.every((row) => this.isAssignmentValid(row));
  });

  private readonly trainingsApi = inject(TrainingsApiService);
  private readonly groupsApi = inject(TrainingGroupsApiService);
  private readonly athletesApi = inject(AthletesApiService);
  private readonly destroyRef = inject(DestroyRef);

  ngAfterViewInit(): void {
    const dialog = this.dialog.nativeElement;
    if (typeof dialog.showModal === 'function') dialog.showModal();
    else dialog.setAttribute('open', '');
    this.loadGroups();
  }

  onFileChange(event: Event): void {
    const input = event.target;
    if (!(input instanceof HTMLInputElement)) return;
    const file = input.files?.item(0) ?? null;
    this.fileError.set(null);
    this.errorMessage.set(null);
    this.document.set(null);
    this.rows.set([]);
    if (!file) {
      this.file.set(null);
      return;
    }
    if (!FILE_EXTENSION.test(file.name)) {
      this.file.set(null);
      this.fileError.set('Wybierz plik z rozszerzeniem .json.');
      input.value = '';
      return;
    }
    if (file.size === 0) {
      this.file.set(null);
      this.fileError.set('Wybrany plik jest pusty.');
      input.value = '';
      return;
    }
    if (file.size > MAX_FILE_BYTES) {
      this.file.set(null);
      this.fileError.set('Plik JSON nie może być większy niż 1 MiB.');
      input.value = '';
      return;
    }
    this.file.set(file);
  }

  validateFile(): void {
    const file = this.file();
    if (!file || this.validating()) return;
    this.errorMessage.set(null);
    this.validating.set(true);
    this.trainingsApi.validateJsonImport(file).pipe(
      take(1),
      catchError((error: unknown) => {
        this.errorMessage.set(this.getErrorMessage(error, 'Nie udało się zweryfikować pliku JSON.'));
        return EMPTY;
      }),
      finalize(() => this.validating.set(false)),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe((document) => {
      this.document.set(document);
      this.rows.set(document.trainings.map((training): TrainingImportRow => ({
        training,
        selected: true,
        trainingDate: '',
        trainingGroupId: this.commonGroupId(),
        assignmentType: 'GROUP',
        athleteIds: [],
      })));
      this.errorMessage.set(null);
    });
  }

  chooseAnotherFile(): void {
    if (this.importing() || this.validating()) return;
    this.document.set(null);
    this.rows.set([]);
    this.file.set(null);
    this.commonGroupId.set(null);
    this.commonTrainingDate.set('');
    this.errorMessage.set(null);
  }

  onCommonGroupChange(event: Event): void {
    const select = event.target;
    if (!(select instanceof HTMLSelectElement)) return;
    const id = select.value === '' ? null : Number(select.value);
    if (id !== null && !this.groups().some((group) => group.id === id)) return;
    this.commonGroupId.set(id);
    this.rows.update((rows) => rows.map((row) => ({ ...row, trainingGroupId: id })));
  }

  onCommonDateChange(event: Event): void {
    const input = event.target;
    if (!(input instanceof HTMLInputElement)) return;
    this.commonTrainingDate.set(input.value);
    this.rows.update((rows) => rows.map((row) => ({ ...row, trainingDate: input.value })));
  }

  onRowSelected(index: number, event: Event): void {
    const input = event.target;
    if (!(input instanceof HTMLInputElement)) return;
    this.updateRow(index, { selected: input.checked });
  }

  onRowGroupChange(index: number, event: Event): void {
    const select = event.target;
    if (!(select instanceof HTMLSelectElement)) return;
    const id = select.value === '' ? null : Number(select.value);
    if (id !== null && !this.groups().some((group) => group.id === id)) return;
    this.updateRow(index, { trainingGroupId: id });
  }

  onRowDateChange(index: number, event: Event): void {
    const input = event.target;
    if (!(input instanceof HTMLInputElement)) return;
    this.updateRow(index, { trainingDate: input.value });
  }

  onAssignmentTypeChange(index: number, event: Event): void {
    const select = event.target;
    if (!(select instanceof HTMLSelectElement)
        || (select.value !== 'GROUP' && select.value !== 'ATHLETES')) return;
    const assignmentType = select.value as TrainingAssignmentType;
    this.updateRow(index, { assignmentType });
    if (assignmentType === 'ATHLETES') this.loadAthletes();
  }

  onAthleteIdsChange(index: number, athleteIds: number[]): void {
    this.updateRow(index, { athleteIds });
  }

  retryLoadingGroups(): void {
    this.loadGroups();
  }

  retryLoadingAthletes(): void {
    this.loadAthletes(true);
  }

  submit(): void {
    if (!this.canImport() || this.importing()) return;
    this.attemptedSubmit.set(true);
    const document = this.document();
    const file = this.file();
    if (!document || !file) return;
    const metadata: TrainingJsonImportMetadata = {
      formatVersion: document.formatVersion,
      trainings: this.rows().map((row) => ({
        selected: row.selected,
        trainingDate: row.trainingDate || null,
        assignmentType: row.training.trainingType === 'TEMPO' ? 'GROUP' : row.assignmentType,
        trainingGroupId: row.training.trainingType === 'TEMPO' || row.assignmentType === 'GROUP'
          ? row.trainingGroupId
          : null,
        athleteIds: row.training.trainingType === 'FREE_TEXT' && row.assignmentType === 'ATHLETES'
          ? row.athleteIds
          : [],
      })),
    };

    this.errorMessage.set(null);
    this.importing.set(true);
    this.trainingsApi.importJson(file, metadata).pipe(
      take(1),
      catchError((error: unknown) => {
        this.errorMessage.set(this.getErrorMessage(error, 'Nie udało się zaimportować treningów.'));
        return EMPTY;
      }),
      finalize(() => this.importing.set(false)),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe((response) => {
      this.closeDialog();
      this.imported.emit(response);
    });
  }

  cancel(): void {
    if (this.validating() || this.importing()) return;
    this.closeDialog();
    this.cancelled.emit();
  }

  onDialogCancel(event: Event): void {
    event.preventDefault();
    this.cancel();
  }

  isAssignmentValid(row: TrainingImportRow): boolean {
    if (row.training.trainingType === 'TEMPO' || row.assignmentType === 'GROUP') {
      return row.trainingGroupId !== null
        && this.groups().some((group) => group.id === row.trainingGroupId);
    }
    const activeAthleteIds = new Set(this.athletes().map((athlete) => athlete.id));
    return row.athleteIds.length > 0 && row.athleteIds.every((id) => activeAthleteIds.has(id));
  }

  assignmentError(row: TrainingImportRow): string | null {
    if (row.training.trainingType === 'TEMPO' || row.assignmentType === 'GROUP') {
      return this.isAssignmentValid(row) ? null : 'Wybierz dostępną grupę treningową.';
    }
    return this.isAssignmentValid(row) ? null : 'Wybierz co najmniej jednego aktywnego zawodnika.';
  }

  isFreeText(training: TrainingTransfer): training is FreeTextTrainingTransfer {
    return training.trainingType === 'FREE_TEXT';
  }

  formatRest(restAfterSeconds: number | null): string {
    return restAfterSeconds === null ? 'bez przerwy' : `przerwa ${restAfterSeconds} s`;
  }

  private updateRow(index: number, changes: Partial<TrainingImportRow>): void {
    this.rows.update((rows) => rows.map((row, rowIndex) =>
      rowIndex === index ? { ...row, ...changes } : row));
  }

  private loadGroups(): void {
    this.groupsLoading.set(true);
    this.groupsError.set(null);
    this.groupsApi.getAccessible().pipe(
      take(1),
      catchError((error: unknown) => {
        this.groupsError.set(this.getErrorMessage(error, 'Nie udało się pobrać dostępnych grup.'));
        return EMPTY;
      }),
      finalize(() => this.groupsLoading.set(false)),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe((groups) => this.groups.set(groups));
  }

  private loadAthletes(force = false): void {
    if (this.athletesLoading() || (!force && this.athletes().length > 0)) return;
    this.athletesLoading.set(true);
    this.athletesError.set(null);
    this.athletesApi.getActive().pipe(
      take(1),
      catchError((error: unknown) => {
        this.athletesError.set(this.getErrorMessage(error, 'Nie udało się pobrać aktywnych zawodników.'));
        return EMPTY;
      }),
      finalize(() => this.athletesLoading.set(false)),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe((athletes) => this.athletes.set(athletes));
  }

  private getErrorMessage(error: unknown, fallback: string): string {
    if (error instanceof HttpErrorResponse && error.status === 401) {
      return 'Sesja wygasła lub użytkownik nie jest zalogowany.';
    }
    if (error instanceof HttpErrorResponse && error.status === 403) {
      return 'Brak dostępu do tej operacji.';
    }
    if (error instanceof HttpErrorResponse && typeof error.error === 'object'
        && error.error !== null && 'detail' in error.error
        && typeof error.error.detail === 'string') {
      return error.error.detail;
    }
    if (error instanceof HttpErrorResponse && error.status === 413) {
      return 'Plik JSON przekracza dozwolony rozmiar 1 MiB.';
    }
    return fallback;
  }

  private closeDialog(): void {
    const dialog = this.dialog.nativeElement;
    if (typeof dialog.close === 'function') dialog.close();
    else dialog.removeAttribute('open');
  }
}

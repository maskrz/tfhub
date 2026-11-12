import { HttpErrorResponse } from '@angular/common/http';
import { AfterViewInit, Component, DestroyRef, ElementRef, EventEmitter, Output, ViewChild, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { EMPTY, catchError, finalize, take } from 'rxjs';
import { TrainingGroup, TrainingGroupsApiService } from '../training-groups/training-groups-api.service';
import { TrainingImportUploadResponse } from './training-import.model';
import { TrainingImportsApiService } from './training-imports-api.service';

const SUPPORTED_IMAGE_TYPES = new Set(['image/jpeg', 'image/png']);
const SUPPORTED_IMAGE_EXTENSIONS = /\.(jpe?g|png)$/i;

@Component({
  imports: [ReactiveFormsModule],
  selector: 'app-training-import-upload-modal',
  styleUrl: './training-import-upload-modal.component.scss',
  templateUrl: './training-import-upload-modal.component.html',
})
export class TrainingImportUploadModalComponent implements AfterViewInit {
  @Output() uploaded = new EventEmitter<TrainingImportUploadResponse>();
  @Output() cancelled = new EventEmitter<void>();
  @ViewChild('dialog') private dialog!: ElementRef<HTMLDialogElement>;

  readonly groups = signal<TrainingGroup[]>([]);
  readonly groupsLoading = signal(false);
  readonly uploading = signal(false);
  readonly attemptedSubmit = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly groupsError = signal<string | null>(null);
  readonly imageError = signal<string | null>(null);
  readonly selectedImage = signal<File | null>(null);
  readonly trainingGroupId = new FormControl<number | null>(null, Validators.required);

  private readonly groupsApi = inject(TrainingGroupsApiService);
  private readonly importsApi = inject(TrainingImportsApiService);
  private readonly destroyRef = inject(DestroyRef);

  ngAfterViewInit(): void {
    const dialog = this.dialog.nativeElement;
    if (typeof dialog.showModal === 'function') dialog.showModal();
    else dialog.setAttribute('open', '');
    this.loadGroups();
  }

  get canSubmit(): boolean {
    const groupId = this.trainingGroupId.value;
    return !this.uploading()
      && groupId !== null
      && this.groups().some((group) => group.id === groupId)
      && this.selectedImage() !== null;
  }

  onGroupChange(event: Event): void {
    const select = event.target;
    if (!(select instanceof HTMLSelectElement)) return;
    const groupId = select.value === '' ? null : Number(select.value);
    if (groupId !== null && !this.groups().some((group) => group.id === groupId)) return;
    this.trainingGroupId.setValue(groupId);
    this.errorMessage.set(null);
  }

  onImageChange(event: Event): void {
    const input = event.target;
    if (!(input instanceof HTMLInputElement)) return;
    const image = input.files?.item(0) ?? null;
    this.imageError.set(null);
    this.errorMessage.set(null);
    if (!image) {
      this.selectedImage.set(null);
      return;
    }

    const supportedType = image.type === ''
      ? SUPPORTED_IMAGE_EXTENSIONS.test(image.name)
      : SUPPORTED_IMAGE_TYPES.has(image.type.toLowerCase());
    if (!supportedType) {
      this.selectedImage.set(null);
      this.imageError.set('Wybierz zdjęcie w formacie JPG, JPEG lub PNG.');
      input.value = '';
      return;
    }
    if (image.size === 0) {
      this.selectedImage.set(null);
      this.imageError.set('Wybrane zdjęcie jest puste.');
      input.value = '';
      return;
    }
    this.selectedImage.set(image);
  }

  retryLoadingGroups(): void {
    this.loadGroups();
  }

  submit(): void {
    if (this.uploading()) return;
    this.attemptedSubmit.set(true);
    this.errorMessage.set(null);
    this.trainingGroupId.markAsTouched();
    if (!this.canSubmit) return;

    const groupId = this.trainingGroupId.value;
    const image = this.selectedImage();
    if (groupId === null || image === null) return;

    this.uploading.set(true);
    this.importsApi.upload(groupId, image).pipe(
      take(1),
      catchError((error: unknown) => {
        this.errorMessage.set(this.getUploadErrorMessage(error));
        return EMPTY;
      }),
      finalize(() => this.uploading.set(false)),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe((response) => {
      this.closeDialog();
      this.uploaded.emit(response);
    });
  }

  cancel(): void {
    if (this.uploading()) return;
    this.closeDialog();
    this.cancelled.emit();
  }

  onDialogCancel(event: Event): void {
    event.preventDefault();
    this.cancel();
  }

  private loadGroups(): void {
    this.groupsLoading.set(true);
    this.groupsError.set(null);
    this.groupsApi.getAccessible().pipe(
      take(1),
      catchError((error: unknown) => {
        this.groupsError.set(this.getGroupsErrorMessage(error));
        return EMPTY;
      }),
      finalize(() => this.groupsLoading.set(false)),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe((groups) => {
      this.groups.set(groups);
      if (this.trainingGroupId.value !== null
          && !groups.some((group) => group.id === this.trainingGroupId.value)) {
        this.trainingGroupId.setValue(null);
      }
    });
  }

  private getGroupsErrorMessage(error: unknown): string {
    if (error instanceof HttpErrorResponse && error.status === 401) {
      return 'Sesja wygasła lub użytkownik nie jest zalogowany.';
    }
    if (error instanceof HttpErrorResponse && error.status === 403) {
      return 'Brak dostępu do grup treningowych.';
    }
    return 'Nie udało się pobrać dostępnych grup treningowych.';
  }

  private getUploadErrorMessage(error: unknown): string {
    if (error instanceof HttpErrorResponse && error.status === 401) {
      return 'Sesja wygasła lub użytkownik nie jest zalogowany.';
    }
    if (error instanceof HttpErrorResponse && error.status === 403) {
      return 'Nie masz dostępu do wybranej grupy treningowej.';
    }
    if (error instanceof HttpErrorResponse && error.status === 404) {
      return 'Nie znaleziono wybranej grupy treningowej.';
    }
    if (error instanceof HttpErrorResponse && (error.status === 400 || error.status === 413)) {
      return 'Nie udało się przesłać zdjęcia. Sprawdź jego format i rozmiar.';
    }
    return 'Nie udało się przesłać zdjęcia. Wybrane dane zachowano — spróbuj ponownie.';
  }

  private closeDialog(): void {
    const dialog = this.dialog.nativeElement;
    if (typeof dialog.close === 'function') dialog.close();
    else dialog.removeAttribute('open');
  }
}

import { HttpErrorResponse } from '@angular/common/http';
import { AfterViewInit, Component, ElementRef, EventEmitter, Input, Output, ViewChild, inject, signal } from '@angular/core';
import { EMPTY, catchError, finalize, take } from 'rxjs';
import { TrainingsApiService } from './trainings-api.service';

@Component({
  selector: 'app-training-delete-confirmation',
  templateUrl: './training-delete-confirmation.component.html',
  styleUrl: './training-delete-confirmation.component.scss',
})
export class TrainingDeleteConfirmationComponent implements AfterViewInit {
  @Input({ required: true }) trainingId!: number;
  @Input({ required: true }) trainingName!: string;
  @Output() deleted = new EventEmitter<number>();
  @Output() cancelled = new EventEmitter<void>();
  @ViewChild('dialog') private dialog!: ElementRef<HTMLDialogElement>;

  readonly deleting = signal(false);
  readonly errorMessage = signal<string | null>(null);

  private readonly api = inject(TrainingsApiService);

  ngAfterViewInit(): void {
    const dialog = this.dialog.nativeElement;
    if (typeof dialog.showModal === 'function') {
      dialog.showModal();
    } else {
      dialog.setAttribute('open', '');
    }
  }

  confirm(): void {
    if (this.deleting()) return;

    this.errorMessage.set(null);
    this.deleting.set(true);
    this.api
      .deleteTraining(this.trainingId)
      .pipe(
        take(1),
        catchError((error: unknown) => {
          this.errorMessage.set(this.getErrorMessage(error));
          return EMPTY;
        }),
        finalize(() => this.deleting.set(false)),
      )
      .subscribe(() => {
        this.closeDialog();
        this.deleted.emit(this.trainingId);
      });
  }

  cancel(): void {
    if (this.deleting()) return;
    this.closeDialog();
    this.cancelled.emit();
  }

  onDialogCancel(event: Event): void {
    event.preventDefault();
    this.cancel();
  }

  private closeDialog(): void {
    const dialog = this.dialog.nativeElement;
    if (typeof dialog.close === 'function') {
      dialog.close();
    } else {
      dialog.removeAttribute('open');
    }
  }

  private getErrorMessage(error: unknown): string {
    if (error instanceof HttpErrorResponse && error.status === 401) {
      return 'Sesja wygasła lub użytkownik nie jest zalogowany.';
    }
    if (error instanceof HttpErrorResponse && error.status === 403) {
      return 'Brak dostępu do usunięcia tego treningu.';
    }
    if (error instanceof HttpErrorResponse && error.status === 404) {
      return 'Nie znaleziono treningu lub brak dostępu. Lista nie została zmieniona.';
    }
    return 'Nie udało się usunąć treningu. Spróbuj ponownie.';
  }
}
